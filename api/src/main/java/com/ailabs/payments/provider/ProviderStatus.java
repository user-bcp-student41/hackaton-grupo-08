package com.ailabs.payments.provider;

/** Estado de un cobro según el proveedor. {@code NOT_SUPPORTED}: el proveedor no puede informarlo (se sigue esperando). */
public record ProviderStatus(Kind kind, String reference, String reasonCode) {

    public enum Kind {
        PENDING,
        SUCCEEDED,
        FAILED,
        NOT_SUPPORTED
    }

    public static ProviderStatus pending() {
        return new ProviderStatus(Kind.PENDING, null, null);
    }

    public static ProviderStatus succeeded(String reference) {
        return new ProviderStatus(Kind.SUCCEEDED, reference, null);
    }

    public static ProviderStatus failed(String reasonCode) {
        return new ProviderStatus(Kind.FAILED, null, reasonCode);
    }

    public static ProviderStatus notSupported() {
        return new ProviderStatus(Kind.NOT_SUPPORTED, null, null);
    }
}
