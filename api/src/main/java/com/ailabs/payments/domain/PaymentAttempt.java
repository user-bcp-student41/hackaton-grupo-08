package com.ailabs.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Intento de pago inmutable. Cada transición devuelve una copia con {@code version} incrementada (control
 * optimista, igual que {@code OnboardingSession}). {@code recipientRef} nunca se devuelve por la API ni se
 * registra en logs o eventos.
 */
public record PaymentAttempt(
        UUID id,
        String userId,
        String walletId,
        String recipientRef,
        String recipientDisplayName,
        String recipientMaskedAlias,
        BigDecimal amount,
        String currency,
        String providerId,
        String channel,
        PaymentStatus status,
        String transactionReference,
        String failureCode,
        UUID retryOf,
        Instant createdAt,
        Instant expiresAt,
        Instant terminalAt,
        int version) {

    /** Transición terminal; solo es válida desde {@code PENDING}. */
    public PaymentAttempt toTerminal(PaymentStatus target, String reference, String failureCode, Instant at) {
        if (status != PaymentStatus.PENDING || !target.isTerminal()) {
            throw new IllegalStateException("Transición de estado no permitida");
        }
        String ref = target == PaymentStatus.SUCCESS ? reference : null;
        String code = target == PaymentStatus.FAILED ? failureCode : null;
        if (target == PaymentStatus.SUCCESS && (ref == null || ref.isBlank())) {
            throw new IllegalStateException("Un pago exitoso requiere referencia de transacción");
        }
        return new PaymentAttempt(id, userId, walletId, recipientRef, recipientDisplayName, recipientMaskedAlias,
                amount, currency, providerId, channel, target, ref, code, retryOf, createdAt, expiresAt, at,
                version + 1);
    }
}
