package com.ailabs.payments.service;

import com.ailabs.payments.provider.RecipientInfo;

/** Resultado de la pre-validación (E1): datos para la pantalla de confirmación. Sin el valor del límite. */
public record ValidationResult(PaymentCommand command, RecipientInfo recipient) {
}
