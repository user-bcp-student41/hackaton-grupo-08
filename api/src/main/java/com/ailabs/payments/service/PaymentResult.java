package com.ailabs.payments.service;

/** Resultado de crear/reintentar: {@code created=false} indica replay de una clave de idempotencia ya usada. */
public record PaymentResult(PaymentView payment, boolean created) {
}
