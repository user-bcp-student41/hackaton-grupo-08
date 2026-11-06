package com.ailabs.payments.provider;

/**
 * Resultado de enviar un cobro. {@code UNKNOWN} (timeout o error técnico) NO equivale a fallo: el intento se
 * mantiene {@code PENDING} (decisión D4).
 */
public record SubmitOutcome(Kind kind, String reference, String reasonCode) {

    public enum Kind {
        ACCEPTED_PENDING,
        SUCCEEDED,
        REJECTED,
        UNKNOWN
    }

    public static SubmitOutcome acceptedPending() {
        return new SubmitOutcome(Kind.ACCEPTED_PENDING, null, null);
    }

    public static SubmitOutcome succeeded(String reference) {
        return new SubmitOutcome(Kind.SUCCEEDED, reference, null);
    }

    public static SubmitOutcome rejected(String reasonCode) {
        return new SubmitOutcome(Kind.REJECTED, null, reasonCode);
    }

    public static SubmitOutcome unknown() {
        return new SubmitOutcome(Kind.UNKNOWN, null, null);
    }
}
