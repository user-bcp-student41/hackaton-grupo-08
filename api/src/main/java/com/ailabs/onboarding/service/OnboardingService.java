package com.ailabs.onboarding.service;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.domain.OnboardingSession;
import com.ailabs.onboarding.domain.SessionStatus;
import com.ailabs.onboarding.domain.StepProgress;
import com.ailabs.onboarding.domain.StepStatus;
import com.ailabs.onboarding.domain.Wallet;
import com.ailabs.onboarding.domain.WalletStatus;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import com.ailabs.onboarding.persistence.OnboardingStore;
import com.ailabs.onboarding.validation.FieldError;
import com.ailabs.onboarding.validation.StepDefinition;
import com.ailabs.onboarding.validation.StepValidator;
import com.ailabs.onboarding.validation.ValidationUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Orquestación del onboarding (C2): iniciar/reanudar, orden de pasos, idempotencia y finalización.
 * Estado y evento se confirman juntos: el evento se agrega primero y el estado se guarda después, dentro de la
 * sección crítica, de modo que un fallo del evento no deja el estado modificado.
 */
@ApplicationScoped
public class OnboardingService {

    public static final String UNATTRIBUTED = "UNATTRIBUTED";
    static final int MAX_ATTEMPTS = 3;
    private static final Pattern ACQUISITION_SOURCE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final Logger LOG = Logger.getLogger(OnboardingService.class);

    private final StepValidator validator;
    private final AnalyticsEventRepository events;
    private final OnboardingStore store;
    private final Clock clock;

    @Inject
    public OnboardingService(StepValidator validator, AnalyticsEventRepository events, OnboardingStore store,
            Clock clock) {
        this.validator = validator;
        this.events = events;
        this.store = store;
        this.clock = clock;
    }

    public StartResult start(String userId, String acquisitionSource) {
        String source = normalizeSource(acquisitionSource);
        return store.withLock(() -> {
            Optional<Wallet> existingWallet = store.findWalletByUser(userId);
            if (existingWallet.isPresent() && existingWallet.get().status() == WalletStatus.ACTIVE) {
                throw ApiException.conflict("ONBOARDING_ALREADY_COMPLETED",
                        "Tu wallet ya está activa. Puedes ir a pagos.", null);
            }
            Optional<OnboardingSession> inProgress = store.findInProgressByUser(userId);
            if (inProgress.isPresent()) {
                return new StartResult(inProgress.get(), false);
            }
            Instant now = clock.instant();
            Wallet wallet = existingWallet.orElseGet(
                    () -> new Wallet("w-" + UUID.randomUUID(), userId, WalletStatus.PENDING, null));
            OnboardingSession session = new OnboardingSession(UUID.randomUUID(), userId, wallet.walletId(),
                    SessionStatus.IN_PROGRESS, source, now, null, 0, initialSteps());
            recordEvent(AnalyticsEvent.started(session, now));
            store.saveWallet(wallet);
            store.saveSession(session);
            return new StartResult(session, true);
        });
    }

    public OnboardingSession get(String userId, UUID sessionId) {
        return loadOwned(userId, sessionId);
    }

    public OnboardingSession submitStep(String userId, UUID sessionId, String stepCode, Map<String, Object> data) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            OnboardingSession snapshot = loadOwned(userId, sessionId);
            StepProgress step = snapshot.step(stepCode).orElseThrow(() -> ApiException.notFound("STEP_NOT_FOUND",
                    "El paso indicado no existe en este onboarding."));
            if (step.status() == StepStatus.COMPLETED) {
                return snapshot;
            }
            if (snapshot.status() == SessionStatus.COMPLETED) {
                throw alreadyCompleted();
            }
            if (step.status() == StepStatus.PENDING) {
                throw ApiException.conflict("STEP_OUT_OF_ORDER", "Primero debes completar el paso actual.",
                        Map.of("currentStepCode", snapshot.currentStepCode()));
            }

            List<FieldError> errors = validate(stepCode, data);
            if (!errors.isEmpty()) {
                throw ApiException.validation(snapshot.currentStepCode(), errors);
            }

            Optional<OnboardingSession> committed = store.withLock(() -> {
                OnboardingSession current = store.findSession(sessionId).orElseThrow();
                if (current.version() != snapshot.version()) {
                    return Optional.<OnboardingSession>empty();
                }
                Instant now = clock.instant();
                OnboardingSession updated = current.completeStep(stepCode, now);
                recordEvent(AnalyticsEvent.stepCompleted(current, stepCode, now));
                store.saveSession(updated);
                return Optional.of(updated);
            });
            if (committed.isPresent()) {
                return committed.get();
            }
        }
        throw ApiException.conflict("CONCURRENT_MODIFICATION",
                "La sesión cambió mientras se procesaba tu solicitud. Inténtalo de nuevo.", null);
    }

    public CompletionResult complete(String userId, UUID sessionId) {
        OnboardingSession owned = loadOwned(userId, sessionId);
        return store.withLock(() -> {
            OnboardingSession current = store.findSession(owned.id()).orElseThrow();
            Wallet wallet = store.findWalletByUser(userId).orElseThrow();
            if (current.status() == SessionStatus.COMPLETED) {
                return new CompletionResult(current, wallet);
            }
            List<String> pending = current.pendingSteps();
            if (!pending.isEmpty()) {
                throw ApiException.conflict("STEPS_INCOMPLETE", "Aún faltan pasos por completar.",
                        Map.of("pendingSteps", pending));
            }
            Instant now = clock.instant();
            OnboardingSession closed = current.complete(now);
            Wallet activated = wallet.activate(now);
            recordEvent(AnalyticsEvent.closed(closed, now));
            store.saveWallet(activated);
            store.saveSession(closed);
            return new CompletionResult(closed, activated);
        });
    }

    public Wallet wallet(String userId) {
        return store.findWalletByUser(userId).orElseThrow(() -> ApiException.notFound("WALLET_NOT_FOUND",
                "Aún no tienes una wallet. Inicia el onboarding para crearla."));
    }

    private List<FieldError> validate(String stepCode, Map<String, Object> data) {
        try {
            return validator.validate(stepCode, data);
        } catch (ValidationUnavailableException e) {
            LOG.warnf("Validador de pasos no disponible (stepCode=%s)", stepCode);
            throw ApiException.validationUnavailable();
        }
    }

    private OnboardingSession loadOwned(String userId, UUID sessionId) {
        OnboardingSession session = store.findSession(sessionId).orElseThrow(() -> ApiException.notFound(
                "SESSION_NOT_FOUND", "No encontramos la sesión de onboarding."));
        if (!session.userId().equals(userId)) {
            throw ApiException.forbidden();
        }
        return session;
    }

    private List<StepProgress> initialSteps() {
        List<StepDefinition> catalog = validator.catalog().stream()
                .sorted(Comparator.comparingInt(StepDefinition::order)).toList();
        if (catalog.isEmpty()) {
            throw new IllegalStateException("El catálogo de pasos está vacío");
        }
        List<StepProgress> steps = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            StepDefinition definition = catalog.get(i);
            steps.add(new StepProgress(definition.stepCode(), definition.order(),
                    i == 0 ? StepStatus.IN_PROGRESS : StepStatus.PENDING, null));
        }
        return steps;
    }

    private void recordEvent(AnalyticsEvent event) {
        try {
            events.append(event);
            LOG.infof("analytics_event type=%s sessionId=%s stepCode=%s occurredAt=%s", event.eventType(),
                    event.sessionId(), event.stepCode(), event.occurredAt());
        } catch (RuntimeException e) {
            LOG.errorf(e, "No se pudo registrar el evento type=%s sessionId=%s", event.eventType(),
                    event.sessionId());
            throw new EventRecordingException(e);
        }
    }

    private static String normalizeSource(String acquisitionSource) {
        if (acquisitionSource == null || acquisitionSource.isBlank()) {
            return UNATTRIBUTED;
        }
        String trimmed = acquisitionSource.trim();
        if (!ACQUISITION_SOURCE.matcher(trimmed).matches()) {
            throw ApiException.badRequest(
                    "El origen de adquisición no es válido. Usa hasta 64 caracteres: letras, números, _ . : o -.");
        }
        return trimmed;
    }

    private static ApiException alreadyCompleted() {
        return ApiException.conflict("ONBOARDING_ALREADY_COMPLETED", "Tu wallet ya está activa. Puedes ir a pagos.",
                null);
    }
}
