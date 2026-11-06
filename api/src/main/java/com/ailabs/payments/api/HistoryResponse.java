package com.ailabs.payments.api;

import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.service.PaymentPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuesta de E5 (historial). */
public record HistoryResponse(List<Item> items, int page, int size, long totalItems) {

    public record Item(
            UUID paymentId,
            String status,
            BigDecimal amount,
            String currency,
            RecipientResponse recipient,
            String transactionReference,
            UUID retryOf,
            Instant createdAt,
            Instant terminalAt) {
    }

    public static HistoryResponse from(PaymentPage page) {
        List<Item> items = page.items().stream().map(view -> {
            PaymentAttempt a = view.attempt();
            return new Item(a.id(), a.status().name(), a.amount(), a.currency(),
                    new RecipientResponse(a.recipientDisplayName(), a.recipientMaskedAlias()),
                    a.transactionReference(), a.retryOf(), a.createdAt(), a.terminalAt());
        }).toList();
        return new HistoryResponse(items, page.page(), page.size(), page.totalItems());
    }
}
