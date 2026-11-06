package com.ailabs.payments.service;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.payments.config.PaymentsConfig;
import com.ailabs.payments.domain.CheckoutEventType;
import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.domain.PaymentStatus;
import com.ailabs.payments.persistence.PaymentStore;
import com.ailabs.payments.provider.PaymentProvider;
import com.ailabs.payments.provider.ProviderStatus;
import com.ailabs.payments.provider.ProviderUnavailableException;
import com.ailabs.payments.provider.RecipientInfo;
import com.ailabs.payments.provider.SubmitOutcome;
import com.ailabs.payments.provider.SubmitRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * B2: orquesta el flujo de pagos (idempotencia, wallet, límite, destinatario, intento, envío al proveedor,
 * transiciones, expiración perezosa, reintento y propiedad). Es el ÚNICO componente que muta estados. Solo conoce
 * el puerto {@link PaymentProvider}, nunca un proveedor concreto.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);
    private static final Set<CheckoutEventType> UI_EVENTS = Set.of(CheckoutEventType.CHECKOUT_CONFIRMATION_VIEWED,
            CheckoutEventType.CHECKOUT_PENDING_SHOWN);

    private final PaymentProvider provider;
    private final PaymentStore store;
    private final CheckoutEventRecorder events;
    private final ProviderChannelLimitPolicy limits;
    private final WalletStatusLookup wallets;
    private final PaymentsConfig config;
    private final Clock clock;

    @Inject
    public PaymentService(PaymentProvider provider, PaymentStore store, CheckoutEventRecorder events,
            ProviderChannelLimitPolicy limits, WalletStatusLookup wallets, PaymentsConfig config, Clock clock) {
        this.provider = provider;
        this.store = store;
        this.events = events;
        this.limits = limits;
        this.wallets = wallets;
        this.config = config;
        this.clock = clock;
    }

    // ---- E1: pre-validación (no persiste nada) ----

    public ValidationResult validate(String userId, PaymentCommand command) {
        requireActiveWallet(userId);
        requireWithinLimit(userId, command.providerId(), command.channel(), command.amount());
        return new ValidationResult(command, resolveRecipient(command));
    }

    // ---- E2: confirmar ----

    public PaymentResult create(String userId, String idempotencyKey, PaymentCommand command) {
        String fingerprint = command.fingerprint();
        Optional<PaymentStore.IdempotencyRecord> existing = store.findIdempotency(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(userId, existing.get().paymentId(), existing.get().fingerprint(), fingerprint);
        }
        String walletId = requireActiveWallet(userId);
        requireWithinLimit(userId, command.providerId(), command.channel(), command.amount());
        RecipientInfo recipient = resolveRecipient(command);
        Instant now = clock.instant();
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), userId, walletId, command.recipientRef(),
                recipient.displayName(), maskedAlias(recipient, command.recipientRef()), command.amount(),
                command.currency(), command.providerId(), command.channel(), PaymentStatus.PENDING, null, null, null,
                now, now.plus(config.pendingExpiration()), null, 0);
        return createAndSubmit(attempt, idempotencyKey, fingerprint);
    }

    // ---- E3: estado ----

    public PaymentView get(String userId, String paymentId) {
        PaymentAttempt attempt = loadOwned(userId, paymentId);
        return view(refresh(attempt, true));
    }

    // ---- E4: reintentar ----

    public PaymentResult retry(String userId, String paymentId, String idempotencyKey) {
        PaymentAttempt original = loadOwned(userId, paymentId);
        String fingerprint = Fingerprints.sha256("RETRY|" + original.id());
        Optional<PaymentStore.IdempotencyRecord> existing = store.findIdempotency(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(userId, existing.get().paymentId(), existing.get().fingerprint(), fingerprint);
        }
        original = refresh(original, true);
        switch (original.status()) {
            case PENDING -> throw ApiException.paymentNotRetryable("PAYMENT_PENDING");
            case SUCCESS -> throw ApiException.paymentNotRetryable("PAYMENT_SUCCEEDED");
            default -> {
                // FAILED o EXPIRED: continúa
            }
        }
        if (store.findRetryChild(original.id()).isPresent()) {
            throw ApiException.paymentNotRetryable("ALREADY_RETRIED");
        }
        if (original.status() == PaymentStatus.EXPIRED) {
            checkProviderBeforeRetry(original);
        }
        String walletId = requireActiveWallet(userId);
        requireWithinLimit(userId, original.providerId(), original.channel(), original.amount());
        Instant now = clock.instant();
        PaymentAttempt retry = new PaymentAttempt(UUID.randomUUID(), userId, walletId, original.recipientRef(),
                original.recipientDisplayName(), original.recipientMaskedAlias(), original.amount(),
                original.currency(), original.providerId(), original.channel(), PaymentStatus.PENDING, null, null,
                original.id(), now, now.plus(config.pendingExpiration()), null, 0);
        return createAndSubmit(retry, idempotencyKey, fingerprint);
    }

    // ---- E5: historial ----

    public PaymentPage history(String userId, PaymentStatus status, int page, int size) {
        for (PaymentAttempt pending : store.findPendingByUser(userId)) {
            refresh(pending, false);
        }
        PaymentStore.Page result = store.history(userId, status, page, size);
        List<PaymentView> items = result.items().stream().map(this::view).toList();
        return new PaymentPage(items, result.page(), result.size(), result.totalItems());
    }

    // ---- E6: eventos de UI ----

    public void recordUiEvent(String userId, CheckoutEventType type, String paymentId) {
        if (!UI_EVENTS.contains(type)) {
            throw ApiException.invalidFields(List.of(java.util.Map.of("field", "eventType", "reason", "NOT_ALLOWED")));
        }
        UUID owned = null;
        if (paymentId != null) {
            owned = loadOwned(userId, paymentId).id();
        }
        if (type == CheckoutEventType.CHECKOUT_PENDING_SHOWN) {
            if (owned == null) {
                throw ApiException.invalidFields(
                        List.of(java.util.Map.of("field", "paymentId", "reason", "REQUIRED")));
            }
            events.pendingShown(userId, owned);
        } else {
            events.confirmationViewed(userId);
        }
    }

    // ---- internos ----

    private PaymentResult createAndSubmit(PaymentAttempt attempt, String key, String fingerprint) {
        PaymentStore.CreateResult result = store.createIdempotent(attempt, key, fingerprint);
        switch (result.kind()) {
            case EXISTING -> {
                return replayOf(result.attempt(), result.fingerprint(), fingerprint);
            }
            case RETRY_ALREADY_EXISTS -> throw ApiException.paymentNotRetryable("ALREADY_RETRIED");
            default -> {
                // CREATED: solo quien crea envía al proveedor (un solo cobro por intento)
            }
        }
        if (attempt.retryOf() == null) {
            events.paymentSubmitted(attempt);
        } else {
            events.retryRequested(attempt);
        }
        return new PaymentResult(view(submitToProvider(attempt)), true);
    }

    private PaymentResult replay(String userId, UUID paymentId, String storedFingerprint, String fingerprint) {
        PaymentAttempt attempt = store.findOwned(userId, paymentId).orElseThrow(ApiException::paymentNotFound);
        return replayOf(attempt, storedFingerprint, fingerprint);
    }

    private PaymentResult replayOf(PaymentAttempt attempt, String storedFingerprint, String fingerprint) {
        if (!storedFingerprint.equals(fingerprint)) {
            throw ApiException.idempotencyKeyConflict();
        }
        return new PaymentResult(view(refresh(attempt, true)), false);
    }

    private PaymentAttempt submitToProvider(PaymentAttempt attempt) {
        SubmitOutcome outcome;
        try {
            outcome = provider.submit(new SubmitRequest(attempt.id(), attempt.amount(), attempt.currency(),
                    attempt.recipientRef(), attempt.providerId(), attempt.channel()));
        } catch (RuntimeException failure) {
            // D4: no se sabe si el cobro se procesó; se mantiene PENDING
            LOG.warnf(failure, "Error técnico al enviar paymentId=%s; el intento sigue PENDING", attempt.id());
            return attempt;
        }
        return switch (outcome.kind()) {
            case SUCCEEDED -> applyTerminal(attempt, PaymentStatus.SUCCESS, outcome.reference(), null);
            case REJECTED -> applyTerminal(attempt, PaymentStatus.FAILED, null, outcome.reasonCode());
            default -> attempt;
        };
    }

    /** Expiración perezosa y, si {@code queryProvider}, resolución por consulta al proveedor (D3). */
    private PaymentAttempt refresh(PaymentAttempt attempt, boolean queryProvider) {
        if (attempt.status() != PaymentStatus.PENDING) {
            return attempt;
        }
        if (!attempt.expiresAt().isAfter(clock.instant())) {
            return applyTerminal(attempt, PaymentStatus.EXPIRED, null, null);
        }
        if (!queryProvider) {
            return attempt;
        }
        ProviderStatus status;
        try {
            status = provider.queryStatus(attempt.id(), attempt.providerId());
        } catch (RuntimeException failure) {
            LOG.warnf(failure, "No se pudo consultar el estado de paymentId=%s; sigue PENDING", attempt.id());
            return attempt;
        }
        return switch (status.kind()) {
            case SUCCEEDED -> applyTerminal(attempt, PaymentStatus.SUCCESS, status.reference(), null);
            case FAILED -> applyTerminal(attempt, PaymentStatus.FAILED, null, status.reasonCode());
            default -> attempt;
        };
    }

    /**
     * Única vía de transición terminal: condicionada a {@code PENDING}. Solo la primera transición gana; un
     * resultado posterior se descarta (CA-2) y un éxito tardío tras un estado terminal distinto se registra (CA-14).
     */
    private PaymentAttempt applyTerminal(PaymentAttempt current, PaymentStatus target, String reference,
            String failureCode) {
        if (current.status() != PaymentStatus.PENDING) {
            lateIfNeeded(current, target);
            return current;
        }
        if (target == PaymentStatus.SUCCESS && (reference == null || reference.isBlank())) {
            LOG.errorf("El proveedor informó éxito sin referencia para paymentId=%s; el intento sigue PENDING",
                    current.id());
            return current;
        }
        PaymentAttempt updated = current.toTerminal(target, reference, failureCode, clock.instant());
        if (store.transition(current, updated)) {
            events.terminal(updated);
            if (target == PaymentStatus.SUCCESS) {
                events.payerActivated(updated);
            }
            return updated;
        }
        PaymentAttempt latest = store.findOwned(current.userId(), current.id()).orElse(current);
        lateIfNeeded(latest, target);
        return latest;
    }

    private void lateIfNeeded(PaymentAttempt latest, PaymentStatus target) {
        if (target == PaymentStatus.SUCCESS && latest.status().isTerminal() && latest.status() != PaymentStatus.SUCCESS) {
            events.lateConfirmationIgnored(latest);
        }
    }

    /** D5: antes de reintentar desde EXPIRED, si el proveedor informa éxito o pendiente no se crea otro cobro. */
    private void checkProviderBeforeRetry(PaymentAttempt expired) {
        ProviderStatus status;
        try {
            status = provider.queryStatus(expired.id(), expired.providerId());
        } catch (RuntimeException failure) {
            LOG.warnf(failure, "Sin respuesta del proveedor antes del reintento de paymentId=%s; se acepta el riesgo R2",
                    expired.id());
            return;
        }
        if (status.kind() == ProviderStatus.Kind.SUCCEEDED) {
            events.lateConfirmationIgnored(expired);
            throw ApiException.paymentNotRetryable("PROVIDER_STATE_UNRESOLVED");
        }
        if (status.kind() == ProviderStatus.Kind.PENDING) {
            throw ApiException.paymentNotRetryable("PROVIDER_STATE_UNRESOLVED");
        }
    }

    private PaymentView view(PaymentAttempt attempt) {
        if (attempt.status() == PaymentStatus.SUCCESS) {
            events.payerActivated(attempt); // idempotente: reintenta si un registro anterior falló
        }
        boolean retryable = (attempt.status() == PaymentStatus.FAILED || attempt.status() == PaymentStatus.EXPIRED)
                && store.findRetryChild(attempt.id()).isEmpty();
        return new PaymentView(attempt, retryable);
    }

    private PaymentAttempt loadOwned(String userId, String paymentId) {
        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.paymentNotFound();
        }
        return store.findOwned(userId, id).orElseThrow(ApiException::paymentNotFound);
    }

    private String requireActiveWallet(String userId) {
        return wallets.activeWalletId(userId).orElseThrow(ApiException::walletNotActive);
    }

    private void requireWithinLimit(String userId, String providerId, String channel, BigDecimal amount) {
        BigDecimal max = limits.maxAmount(providerId, channel).orElseThrow(ApiException::providerChannelUnsupported);
        if (amount.compareTo(max) > 0) { // inclusivo (S7): igual al máximo se acepta
            events.limitRejected(userId, providerId, channel);
            throw ApiException.amountLimitExceeded();
        }
    }

    private RecipientInfo resolveRecipient(PaymentCommand command) {
        try {
            return provider.resolveRecipient(command.recipientRef(), command.providerId(), command.channel())
                    .orElseThrow(ApiException::recipientNotFound);
        } catch (ProviderUnavailableException unavailable) {
            throw ApiException.providerUnavailable();
        }
    }

    /** S6: si el proveedor no devuelve alias, se enmascara el {@code recipientRef}. */
    private static String maskedAlias(RecipientInfo info, String recipientRef) {
        if (info.maskedAlias() != null && !info.maskedAlias().isBlank()) {
            return info.maskedAlias();
        }
        String tail = recipientRef.length() <= 3 ? recipientRef : recipientRef.substring(recipientRef.length() - 3);
        return "***" + tail;
    }
}
