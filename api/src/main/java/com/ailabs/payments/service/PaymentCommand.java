package com.ailabs.payments.service;

import java.math.BigDecimal;

/** Petición de pago ya validada en su forma (importe positivo con ≤ 2 decimales, moneda PEN, textos acotados). */
public record PaymentCommand(String recipientRef, BigDecimal amount, String currency, String providerId,
        String channel) {

    public PaymentCommand {
        amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    /** Huella estable para detectar cuerpos distintos bajo la misma {@code Idempotency-Key}. */
    public String fingerprint() {
        return Fingerprints.sha256(String.join("|", recipientRef, amount.toPlainString(), currency, providerId,
                channel));
    }
}
