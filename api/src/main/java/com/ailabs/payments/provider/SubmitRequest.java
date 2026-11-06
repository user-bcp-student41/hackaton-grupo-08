package com.ailabs.payments.provider;

import java.math.BigDecimal;
import java.util.UUID;

/** Solicitud de cobro al proveedor. {@code paymentId} es la referencia propia que garantiza idempotencia hacia el proveedor. */
public record SubmitRequest(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String recipientRef,
        String providerId,
        String channel) {
}
