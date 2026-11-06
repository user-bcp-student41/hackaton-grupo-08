package com.ailabs.payments.domain;

/** Estados del intento de pago. {@code PENDING} es intermedio; los otros tres son terminales e inmutables. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    EXPIRED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
