package com.ailabs.payments.api;

import com.ailabs.payments.service.ValidationResult;
import java.math.BigDecimal;

/** Respuesta de E1: datos para la pantalla de confirmación. No expone el valor del límite (P6). */
public record ValidationResponse(
        boolean valid,
        RecipientResponse recipient,
        BigDecimal amount,
        String currency,
        String providerId,
        String channel) {

    public static ValidationResponse from(ValidationResult result) {
        return new ValidationResponse(true,
                new RecipientResponse(result.recipient().displayName(), result.recipient().maskedAlias()),
                result.command().amount(), result.command().currency(), result.command().providerId(),
                result.command().channel());
    }
}
