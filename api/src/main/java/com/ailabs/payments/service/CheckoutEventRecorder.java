package com.ailabs.payments.service;

import com.ailabs.payments.domain.CheckoutEvent;
import com.ailabs.payments.domain.CheckoutEventType;
import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.persistence.CheckoutEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * B6: traduce hitos a eventos de checkout. Un fallo al registrar NUNCA cambia el resultado del pago (CA-16): se
 * captura y se registra en log sin datos personales. Para {@code PAYER_ACTIVATED} el reintento es idempotente
 * (por {@code dedupeKey = paymentId}) y se vuelve a intentar en la siguiente lectura del pago.
 */
@ApplicationScoped
public class CheckoutEventRecorder {

    private static final Logger LOG = Logger.getLogger(CheckoutEventRecorder.class);

    private final CheckoutEventRepository repository;
    private final Clock clock;

    @Inject
    public CheckoutEventRecorder(CheckoutEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void confirmationViewed(String userId) {
        record(type(CheckoutEventType.CHECKOUT_CONFIRMATION_VIEWED), userId, null, null, null, null, null, null,
                null);
    }

    public void limitRejected(String userId, String providerId, String channel) {
        record(type(CheckoutEventType.CHECKOUT_LIMIT_REJECTED), userId, null, null, providerId, channel, null, null,
                null);
    }

    public void paymentSubmitted(PaymentAttempt attempt) {
        record(type(CheckoutEventType.CHECKOUT_PAYMENT_SUBMITTED), attempt.userId(), attempt.id(), null, null, null,
                null, null, "SUBMITTED|" + attempt.id());
    }

    public void pendingShown(String userId, UUID paymentId) {
        record(type(CheckoutEventType.CHECKOUT_PENDING_SHOWN), userId, paymentId, null, null, null, null, null,
                "PENDING_SHOWN|" + paymentId);
    }

    public void retryRequested(PaymentAttempt retry) {
        record(type(CheckoutEventType.CHECKOUT_RETRY_REQUESTED), retry.userId(), retry.id(), retry.retryOf(), null,
                null, null, null, "RETRY|" + retry.id());
    }

    public void terminal(PaymentAttempt attempt) {
        record(type(CheckoutEventType.CHECKOUT_PAYMENT_TERMINAL), attempt.userId(), attempt.id(), null, null, null,
                attempt.status().name(), null, "TERMINAL|" + attempt.id());
    }

    /** Idempotente: se puede invocar en cada lectura de un pago {@code SUCCESS}. */
    public void payerActivated(PaymentAttempt attempt) {
        record(type(CheckoutEventType.PAYER_ACTIVATED), attempt.userId(), attempt.id(), null, null, null, null,
                attempt.transactionReference(), attempt.id().toString());
    }

    public void lateConfirmationIgnored(PaymentAttempt attempt) {
        LOG.warnf("Confirmación tardía del proveedor ignorada: paymentId=%s estadoTerminal=%s", attempt.id(),
                attempt.status());
        record(type(CheckoutEventType.CHECKOUT_LATE_CONFIRMATION_IGNORED), attempt.userId(), attempt.id(), null,
                null, null, null, null, "LATE|" + attempt.id());
    }

    private static CheckoutEventType type(CheckoutEventType type) {
        return type;
    }

    private void record(CheckoutEventType type, String userId, UUID paymentId, UUID retryOf, String providerId,
            String channel, String outcome, String reference, String dedupeKey) {
        try {
            repository.append(new CheckoutEvent(UUID.randomUUID(), type, clock.instant(), userId, paymentId, retryOf,
                    providerId, channel, outcome, reference, dedupeKey));
        } catch (RuntimeException failure) {
            LOG.errorf(failure, "No se pudo registrar el evento %s (paymentId=%s); el pago no se ve afectado", type,
                    paymentId);
        }
    }
}
