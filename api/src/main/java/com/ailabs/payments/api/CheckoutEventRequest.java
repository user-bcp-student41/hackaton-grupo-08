package com.ailabs.payments.api;

/** Cuerpo de E6; {@code paymentId} es opcional salvo para {@code CHECKOUT_PENDING_SHOWN}. */
public record CheckoutEventRequest(String eventType, String paymentId) {
}
