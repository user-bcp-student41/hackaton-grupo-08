package com.ailabs.payments.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento append-only del funnel de checkout. Solo identificadores: sin nombre, alias, {@code recipientRef} ni
 * importes. {@code occurredAt} proviene del reloj de servidor. {@code dedupeKey} (opcional) garantiza que un
 * mismo hito no se registre dos veces.
 */
public record CheckoutEvent(
        UUID eventId,
        CheckoutEventType eventType,
        Instant occurredAt,
        String userId,
        UUID paymentId,
        UUID retryOf,
        String providerId,
        String channel,
        String outcome,
        String transactionReference,
        String dedupeKey) {
}
