package com.ailabs.payments.provider;

/** El proveedor no respondió o devolvió un error técnico. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }
}
