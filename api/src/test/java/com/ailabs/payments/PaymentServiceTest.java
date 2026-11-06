package com.ailabs.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.payments.config.PaymentsConfig;
import com.ailabs.payments.domain.CheckoutEvent;
import com.ailabs.payments.domain.CheckoutEventType;
import com.ailabs.payments.domain.PaymentStatus;
import com.ailabs.payments.persistence.CheckoutEventRepository;
import com.ailabs.payments.persistence.InMemoryCheckoutEventRepository;
import com.ailabs.payments.persistence.InMemoryPaymentStore;
import com.ailabs.payments.provider.InProcessMockPaymentProvider;
import com.ailabs.payments.service.CheckoutEventRecorder;
import com.ailabs.payments.service.PaymentCommand;
import com.ailabs.payments.service.PaymentResult;
import com.ailabs.payments.service.PaymentService;
import com.ailabs.payments.service.PaymentView;
import com.ailabs.payments.service.ProviderChannelLimitPolicy;
import com.ailabs.payments.service.WalletStatusLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de la lógica de pagos sin Quarkus: reloj controlable, mock en proceso y almacenes en memoria. */
class PaymentServiceTest {

    private static final String USER = "u-demo-1";
    private static final String PROVIDER = "mock-provider-a";
    private static final String CHANNEL = "ALIAS";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T17:00:00Z"));
    private InMemoryPaymentStore store;
    private InMemoryCheckoutEventRepository eventRepo;
    private InProcessMockPaymentProvider provider;
    private PaymentService service;
    private boolean walletActive;

    @BeforeEach
    void setUp() {
        walletActive = true;
        store = new InMemoryPaymentStore();
        eventRepo = new InMemoryCheckoutEventRepository();
        provider = new InProcessMockPaymentProvider();
        service = newService(eventRepo);
    }

    private PaymentService newService(CheckoutEventRepository repository) {
        PaymentsConfig config = new PaymentsConfig() {
            @Override
            public Duration pendingExpiration() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Map<String, Map<String, ChannelLimit>> limits() {
                return Map.of(PROVIDER, Map.of(CHANNEL, (ChannelLimit) () -> new BigDecimal("500.00")));
            }
        };
        WalletStatusLookup wallets = userId -> walletActive ? Optional.of("w-" + userId) : Optional.empty();
        return new PaymentService(provider, store, new CheckoutEventRecorder(repository, clock),
                new ProviderChannelLimitPolicy(config), wallets, config, clock);
    }

    private static PaymentCommand command(String recipientRef, String amount) {
        return new PaymentCommand(recipientRef, new BigDecimal(amount), "PEN", PROVIDER, CHANNEL);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private List<CheckoutEvent> events(CheckoutEventType type) {
        return eventRepo.findAll().stream().filter(e -> e.eventType() == type).toList();
    }

    private static ApiException error(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    // ---- HU2-AC1 / EP-02 ----

    @Test
    void validateReturnsRecipientAndAmountWithoutCreatingAnAttempt() {
        var result = service.validate(USER, command("contacto-123", "50.00"));

        assertEquals("Contacto de prueba", result.recipient().displayName());
        assertEquals("***123", result.recipient().maskedAlias());
        assertEquals(new BigDecimal("50.00"), result.command().amount());
        assertEquals(0, store.history(USER, null, 0, 20).totalItems());
        assertEquals(0, provider.submitCount(UUID.randomUUID()));
    }

    @Test
    void amountEqualToLimitIsAcceptedAndAboveIsRejectedBeforeSubmitting() {
        PaymentResult ok = service.create(USER, key(), command("mock-instant", "500.00"));
        assertEquals(PaymentStatus.SUCCESS, ok.payment().attempt().status());

        ApiException exceeded = error(() -> service.create(USER, key(), command("mock-instant", "500.01")));
        assertEquals(422, exceeded.status());
        assertEquals("AMOUNT_LIMIT_EXCEEDED", exceeded.code());
        assertEquals(Map.of("field", "amount"), exceeded.details());
        assertEquals(1, store.history(USER, null, 0, 20).totalItems());
        assertEquals(1, events(CheckoutEventType.CHECKOUT_LIMIT_REJECTED).size());
    }

    @Test
    void unconfiguredProviderChannelIsUnsupported() {
        PaymentCommand other = new PaymentCommand("mock-instant", new BigDecimal("1.00"), "PEN", "otro", CHANNEL);
        ApiException unsupported = error(() -> service.validate(USER, other));
        assertEquals(422, unsupported.status());
        assertEquals("PROVIDER_CHANNEL_UNSUPPORTED", unsupported.code());
    }

    @Test
    void inactiveWalletBlocksPaymentWithoutAttempt() {
        walletActive = false;
        ApiException blocked = error(() -> service.create(USER, key(), command("mock-instant", "10.00")));
        assertEquals(409, blocked.status());
        assertEquals("WALLET_NOT_ACTIVE", blocked.code());
        assertEquals(0, store.history(USER, null, 0, 20).totalItems());
    }

    @Test
    void unknownRecipientAndUnavailableProviderAreReportedBeforeCreatingAnAttempt() {
        ApiException notFound = error(() -> service.validate(USER, command("mock-not-found", "10.00")));
        assertEquals(404, notFound.status());
        assertEquals("RECIPIENT_NOT_FOUND", notFound.code());

        ApiException unavailable = error(() -> service.create(USER, key(), command("mock-unavailable", "10.00")));
        assertEquals(503, unavailable.status());
        assertEquals("PROVIDER_UNAVAILABLE", unavailable.code());
        assertEquals(0, store.history(USER, null, 0, 20).totalItems());
    }

    // ---- HU2-AC2 / HU2-AC3 / HU2-AC6 ----

    @Test
    void pendingIsNotTerminalAndResolvesToSingleSuccessWithReferenceHistoryAndActivation() {
        PaymentResult created = service.create(USER, key(), command("mock-success", "50.00"));
        String id = created.payment().attempt().id().toString();
        assertTrue(created.created());
        assertEquals(PaymentStatus.PENDING, created.payment().attempt().status());
        assertFalse(created.payment().terminal());
        assertFalse(created.payment().retryable());
        assertNull(created.payment().attempt().transactionReference());

        PaymentView stillPending = service.get(USER, id);
        assertEquals(PaymentStatus.PENDING, stillPending.attempt().status());
        assertTrue(events(CheckoutEventType.PAYER_ACTIVATED).isEmpty());

        PaymentView done = service.get(USER, id);
        assertEquals(PaymentStatus.SUCCESS, done.attempt().status());
        assertTrue(done.terminal());
        assertNotNull(done.attempt().transactionReference());
        assertNotNull(done.attempt().terminalAt());

        service.get(USER, id);
        service.get(USER, id);
        assertEquals(1, events(CheckoutEventType.PAYER_ACTIVATED).size());
        assertEquals(1, events(CheckoutEventType.CHECKOUT_PAYMENT_TERMINAL).size());
        CheckoutEvent activation = events(CheckoutEventType.PAYER_ACTIVATED).get(0);
        assertEquals(USER, activation.userId());
        assertEquals(done.attempt().id(), activation.paymentId());
        assertEquals(done.attempt().transactionReference(), activation.transactionReference());

        var history = service.history(USER, null, 0, 20);
        assertEquals(1, history.totalItems());
        assertEquals(done.attempt().transactionReference(), history.items().get(0).attempt().transactionReference());
    }

    @Test
    void failedAndExpiredAndTimeoutNeverEmitActivation() {
        PaymentResult rejected = service.create(USER, key(), command("mock-rejected", "10.00"));
        assertEquals(PaymentStatus.FAILED, rejected.payment().attempt().status());
        assertEquals("PROVIDER_REJECTED", rejected.payment().attempt().failureCode());

        PaymentResult timeout = service.create(USER, key(), command("mock-timeout", "10.00"));
        assertEquals(PaymentStatus.PENDING, timeout.payment().attempt().status()); // D4: no es FAILED

        clock.advance(Duration.ofMinutes(6));
        PaymentView expired = service.get(USER, timeout.payment().attempt().id().toString());
        assertEquals(PaymentStatus.EXPIRED, expired.attempt().status());
        assertNull(expired.attempt().transactionReference());
        assertTrue(events(CheckoutEventType.PAYER_ACTIVATED).isEmpty());
        assertEquals(2, events(CheckoutEventType.CHECKOUT_PAYMENT_TERMINAL).size());
    }

    @Test
    void asynchronousFailureIsTerminalAndRetryable() {
        PaymentResult created = service.create(USER, key(), command("mock-fail-async", "10.00"));
        String id = created.payment().attempt().id().toString();
        assertEquals(PaymentStatus.PENDING, service.get(USER, id).attempt().status());
        PaymentView failed = service.get(USER, id);
        assertEquals(PaymentStatus.FAILED, failed.attempt().status());
        assertTrue(failed.retryable());
    }

    // ---- HU2-AC4 / idempotencia ----

    @Test
    void sameKeyAndBodyReturnsSameAttemptAndSubmitsOnce() {
        String key = key();
        PaymentResult first = service.create(USER, key, command("mock-success", "20.00"));
        PaymentResult second = service.create(USER, key, command("mock-success", "20.00"));

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.payment().attempt().id(), second.payment().attempt().id());
        assertEquals(1, provider.submitCount(first.payment().attempt().id()));
        assertEquals(1, store.history(USER, null, 0, 20).totalItems());
    }

    @Test
    void sameKeyWithDifferentBodyIsConflict() {
        String key = key();
        service.create(USER, key, command("mock-success", "20.00"));
        ApiException conflict = error(() -> service.create(USER, key, command("mock-success", "21.00")));
        assertEquals(409, conflict.status());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.code());
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOneAttemptAndOneSubmit() throws Exception {
        String key = key();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<UUID> task = () -> {
                start.await();
                return service.create(USER, key, command("mock-success", "30.00")).payment().attempt().id();
            };
            futures.add(pool.submit(task));
        }
        start.countDown();
        Set<UUID> ids = new HashSet<>();
        for (Future<UUID> future : futures) {
            ids.add(future.get());
        }
        pool.shutdown();

        assertEquals(1, ids.size());
        assertEquals(1, provider.submitCount(ids.iterator().next()));
        assertEquals(1, store.history(USER, null, 0, 20).totalItems());
    }

    @Test
    void retryCreatesOneNewAttemptWithoutTouchingTheOriginalAndIsIdempotent() {
        PaymentResult failed = service.create(USER, key(), command("mock-rejected", "10.00"));
        String originalId = failed.payment().attempt().id().toString();
        assertTrue(service.get(USER, originalId).retryable());

        String retryKey = key();
        PaymentResult retry = service.retry(USER, originalId, retryKey);
        PaymentResult replay = service.retry(USER, originalId, retryKey);

        assertTrue(retry.created());
        assertFalse(replay.created());
        assertEquals(retry.payment().attempt().id(), replay.payment().attempt().id());
        assertEquals(failed.payment().attempt().id(), retry.payment().attempt().retryOf());
        assertNotNullKey(retry);
        assertEquals(1, provider.submitCount(retry.payment().attempt().id()));
        assertEquals(1, events(CheckoutEventType.CHECKOUT_RETRY_REQUESTED).size());

        PaymentView original = service.get(USER, originalId);
        assertEquals(PaymentStatus.FAILED, original.attempt().status());
        assertFalse(original.retryable());

        ApiException again = error(() -> service.retry(USER, originalId, key()));
        assertEquals("PAYMENT_NOT_RETRYABLE", again.code());
        assertEquals(Map.of("reason", "ALREADY_RETRIED"), again.details());
        assertEquals(2, store.history(USER, null, 0, 20).totalItems());
    }

    private static void assertNotNullKey(PaymentResult result) {
        assertNotNull(result.payment().attempt().retryOf());
    }

    @Test
    void retryIsNotAllowedWhilePendingOrAfterSuccess() {
        PaymentResult pending = service.create(USER, key(), command("mock-never-resolves", "10.00"));
        ApiException pendingError = error(() -> service.retry(USER, pending.payment().attempt().id().toString(), key()));
        assertEquals(Map.of("reason", "PAYMENT_PENDING"), pendingError.details());

        PaymentResult success = service.create(USER, key(), command("mock-instant", "10.00"));
        ApiException successError = error(() -> service.retry(USER, success.payment().attempt().id().toString(), key()));
        assertEquals(Map.of("reason", "PAYMENT_SUCCEEDED"), successError.details());
    }

    @Test
    void expiredPaymentCanBeRetried() {
        PaymentResult pending = service.create(USER, key(), command("mock-never-resolves", "10.00"));
        clock.advance(Duration.ofMinutes(5)); // expiresAt <= ahora
        String id = pending.payment().attempt().id().toString();
        assertEquals(PaymentStatus.EXPIRED, service.get(USER, id).attempt().status());
        assertTrue(service.get(USER, id).retryable());

        PaymentResult retry = service.retry(USER, id, key());
        assertEquals(PaymentStatus.PENDING, retry.payment().attempt().status());
        assertEquals(pending.payment().attempt().id(), retry.payment().attempt().retryOf());
    }

    // ---- CA-14 ----

    @Test
    void lateProviderSuccessAfterExpirationNeverChangesStateAndIsRecorded() {
        PaymentResult created = service.create(USER, key(), command("mock-late-success", "10.00"));
        String id = created.payment().attempt().id().toString();
        assertEquals(PaymentStatus.PENDING, service.get(USER, id).attempt().status()); // consulta 1: PENDING
        clock.advance(Duration.ofMinutes(6));
        assertEquals(PaymentStatus.EXPIRED, service.get(USER, id).attempt().status());

        // el reintento consulta al proveedor (consulta 2: SUCCEEDED) y se bloquea para no duplicar el cobro
        ApiException blocked = error(() -> service.retry(USER, id, key()));
        assertEquals(409, blocked.status());
        assertEquals(Map.of("reason", "PROVIDER_STATE_UNRESOLVED"), blocked.details());

        PaymentView still = service.get(USER, id);
        assertEquals(PaymentStatus.EXPIRED, still.attempt().status());
        assertNull(still.attempt().transactionReference());
        assertEquals(1, events(CheckoutEventType.CHECKOUT_LATE_CONFIRMATION_IGNORED).size());
        assertTrue(events(CheckoutEventType.PAYER_ACTIVATED).isEmpty());
    }

    // ---- CA-16 / CA-17 ----

    @Test
    void eventRecordingFailureDoesNotChangeThePaymentOutcome() {
        CheckoutEventRepository failing = new CheckoutEventRepository() {
            @Override
            public boolean append(CheckoutEvent event) {
                throw new IllegalStateException("almacén de eventos caído");
            }

            @Override
            public List<CheckoutEvent> findAll() {
                return List.of();
            }
        };
        PaymentService degraded = newService(failing);

        PaymentResult result = degraded.create(USER, key(), command("mock-instant", "10.00"));

        assertEquals(PaymentStatus.SUCCESS, result.payment().attempt().status());
        assertNotNull(result.payment().attempt().transactionReference());
    }

    @Test
    void paymentsOfOtherUsersAndMalformedIdsAreNotFound() {
        PaymentResult mine = service.create(USER, key(), command("mock-instant", "10.00"));
        String id = mine.payment().attempt().id().toString();

        assertEquals("PAYMENT_NOT_FOUND", error(() -> service.get("u-otro", id)).code());
        assertEquals("PAYMENT_NOT_FOUND", error(() -> service.retry("u-otro", id, key())).code());
        assertEquals("PAYMENT_NOT_FOUND", error(() -> service.get(USER, "no-es-uuid")).code());
        assertEquals(404, error(() -> service.get(USER, UUID.randomUUID().toString())).status());
        assertEquals(0, service.history("u-otro", null, 0, 20).totalItems());
    }

    @Test
    void historyIsPagedFilteredAndNewestFirst() {
        service.create(USER, key(), command("mock-instant", "10.00"));
        clock.advance(Duration.ofSeconds(1));
        service.create(USER, key(), command("mock-rejected", "11.00"));
        clock.advance(Duration.ofSeconds(1));
        service.create(USER, key(), command("mock-instant", "12.00"));

        var all = service.history(USER, null, 0, 2);
        assertEquals(3, all.totalItems());
        assertEquals(2, all.items().size());
        assertEquals(new BigDecimal("12.00"), all.items().get(0).attempt().amount());

        var successes = service.history(USER, PaymentStatus.SUCCESS, 0, 20);
        assertEquals(2, successes.totalItems());
    }

    @Test
    void uiEventsAreRecordedOnlyForOwnedPayments() {
        service.recordUiEvent(USER, CheckoutEventType.CHECKOUT_CONFIRMATION_VIEWED, null);
        assertEquals(1, events(CheckoutEventType.CHECKOUT_CONFIRMATION_VIEWED).size());

        PaymentResult pending = service.create(USER, key(), command("mock-never-resolves", "10.00"));
        String id = pending.payment().attempt().id().toString();
        service.recordUiEvent(USER, CheckoutEventType.CHECKOUT_PENDING_SHOWN, id);
        service.recordUiEvent(USER, CheckoutEventType.CHECKOUT_PENDING_SHOWN, id);
        assertEquals(1, events(CheckoutEventType.CHECKOUT_PENDING_SHOWN).size());

        assertEquals("PAYMENT_NOT_FOUND",
                error(() -> service.recordUiEvent("u-otro", CheckoutEventType.CHECKOUT_PENDING_SHOWN, id)).code());
        assertEquals("INVALID_REQUEST",
                error(() -> service.recordUiEvent(USER, CheckoutEventType.PAYER_ACTIVATED, null)).code());
        assertEquals("INVALID_REQUEST",
                error(() -> service.recordUiEvent(USER, CheckoutEventType.CHECKOUT_PENDING_SHOWN, null)).code());
    }

    /** Reloj controlable para probar expiración sin esperas. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
