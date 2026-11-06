package com.ailabs.onboarding.validation;

public record FieldError(String field, String code, String message, String howToFix) {
}
