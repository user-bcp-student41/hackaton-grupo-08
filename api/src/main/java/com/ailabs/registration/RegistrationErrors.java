package com.ailabs.registration;

import com.ailabs.onboarding.api.ApiException;

/** Factorías de los códigos de error nuevos de HU-01 (mensajes accionables, sin datos sensibles). */
public final class RegistrationErrors {

    private RegistrationErrors() {
    }

    public static ApiException registrationNotFound() {
        return ApiException.notFound("REGISTRATION_NOT_FOUND", "No encontramos este registro. Inicia uno nuevo.");
    }

    public static ApiException documentAlreadyRegistered() {
        return ApiException.conflict("DOCUMENT_ALREADY_REGISTERED",
                "Este documento ya tiene una cuenta. Inicia sesión.", null);
    }

    public static ApiException alreadyCompleted() {
        return ApiException.conflict("REGISTRATION_ALREADY_COMPLETED",
                "Tu registro ya está completo. Inicia sesión.", null);
    }

    public static ApiException otpInvalid() {
        return new ApiException(422, "OTP_INVALID",
                "El código no es correcto. Revísalo o solicita uno nuevo.", null);
    }

    public static ApiException otpExpired() {
        return new ApiException(422, "OTP_EXPIRED", "El código venció. Solicita uno nuevo para continuar.", null);
    }

    public static ApiException otpAttemptsExceeded() {
        return new ApiException(429, "OTP_ATTEMPTS_EXCEEDED",
                "Superaste los intentos permitidos. Solicita un código nuevo.", null);
    }

    public static ApiException otpResendLimit() {
        return new ApiException(429, "OTP_RESEND_LIMIT",
                "Alcanzaste el límite de reenvíos. Inténtalo más tarde.", null);
    }

    public static ApiException otpDeliveryUnavailable() {
        return new ApiException(503, "OTP_DELIVERY_UNAVAILABLE",
                "No pudimos enviar el código. Solicita el reenvío en unos minutos.", null);
    }

    public static ApiException invalidCredentials() {
        return new ApiException(401, "INVALID_CREDENTIALS", "El documento o la contraseña no son correctos.", null);
    }

    public static ApiException customerNotValidated() {
        return new ApiException(403, "CUSTOMER_NOT_VALIDATED",
                "Aún no validaste tu correo. Completa el código de verificación para ingresar.", null);
    }

    public static ApiException loginRateLimited() {
        return new ApiException(429, "LOGIN_RATE_LIMITED",
                "Demasiados intentos. Espera unos minutos antes de volver a intentar.", null);
    }
}
