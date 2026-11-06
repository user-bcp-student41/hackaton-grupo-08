package com.ailabs.onboarding.validation;

/** El proveedor/mock de validación no respondió o falló técnicamente. */
public class ValidationUnavailableException extends RuntimeException {

    public ValidationUnavailableException(String message) {
        super(message);
    }
}
