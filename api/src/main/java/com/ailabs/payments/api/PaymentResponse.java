package com.ailabs.payments.api;

import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.service.PaymentView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Objeto {@code Payment} (E2, E3, E4). No incluye {@code recipientRef} ni etiquetas de texto (D7). */
public record PaymentResponse(
        UUID paymentId,
        String status,
        boolean terminal,
        boolean retryable,
        BigDecimal amount,
        String currency,
        RecipientResponse recipient,
        String providerId,
        String channel,
        String transactionReference,
        String failureCode,
        UUID retryOf,
        Instant createdAt,
        Instant terminalAt) {

    public static PaymentResponse from(PaymentView view) {
        PaymentAttempt a = view.attempt();
        return new PaymentResponse(a.id(), a.status().name(), view.terminal(), view.retryable(), a.amount(),
                a.currency(), new RecipientResponse(a.recipientDisplayName(), a.recipientMaskedAlias()),
                a.providerId(), a.channel(), a.transactionReference(), a.failureCode(), a.retryOf(), a.createdAt(),
                a.terminalAt());
    }
}
