package com.ailabs.onboarding.api;

import java.util.Map;

/** Formato común de error: {@code code} y {@code message} obligatorios; {@code details} opcional. */
public record ApiError(String code, String message, Map<String, Object> details) {

    public ApiError {
        details = details == null ? Map.of() : details;
    }
}
