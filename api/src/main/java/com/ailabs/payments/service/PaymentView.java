package com.ailabs.payments.service;

import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.domain.PaymentStatus;

/** Intento de pago más los datos derivados que necesita la API ({@code terminal}, {@code retryable}). */
public record PaymentView(PaymentAttempt attempt, boolean retryable) {

    public boolean terminal() {
        return attempt.status() != PaymentStatus.PENDING;
    }
}
