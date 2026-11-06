package com.ailabs.registration;

/** Error de validación por campo: {@code field}, {@code code} y un mensaje accionable (sin el valor enviado). */
public record FieldIssue(String field, String code, String message) {
}
