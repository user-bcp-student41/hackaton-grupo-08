package com.ailabs.payments.domain;

/**
 * Catálogo propuesto de eventos del funnel de checkout y de activación del pagador (sujeto a validación de
 * Producto, preguntas P5/P10 del contrato).
 */
public enum CheckoutEventType {
    CHECKOUT_CONFIRMATION_VIEWED,
    CHECKOUT_LIMIT_REJECTED,
    CHECKOUT_PAYMENT_SUBMITTED,
    CHECKOUT_PENDING_SHOWN,
    CHECKOUT_RETRY_REQUESTED,
    CHECKOUT_PAYMENT_TERMINAL,
    PAYER_ACTIVATED,
    CHECKOUT_LATE_CONFIRMATION_IGNORED
}
