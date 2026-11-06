package com.ailabs.onboarding.api;

import java.util.List;
import java.util.Map;

/** Error de negocio o de contrato con su código HTTP; se traduce a {@link ApiError} en el mapper. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Map<String, Object> details;

    public ApiException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_REQUEST", message, null);
    }

    public static ApiException unauthenticated() {
        return new ApiException(401, "UNAUTHENTICATED", "Necesitas iniciar sesión para continuar.", null);
    }

    public static ApiException forbidden() {
        return new ApiException(403, "SESSION_FORBIDDEN", "No tienes acceso a esta sesión de onboarding.", null);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(404, code, message, null);
    }

    public static ApiException conflict(String code, String message, Map<String, Object> details) {
        return new ApiException(409, code, message, details);
    }

    public static ApiException validation(String currentStepCode, List<?> errors) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("currentStepCode", currentStepCode);
        details.put("errors", errors);
        return new ApiException(422, "VALIDATION_ERROR", "Revisa los datos marcados para continuar.", details);
    }

    public static ApiException validationUnavailable() {
        return new ApiException(503, "VALIDATION_UNAVAILABLE",
                "No pudimos validar tu información, inténtalo de nuevo.", null);
    }

    public static ApiException internal() {
        return new ApiException(500, "INTERNAL_ERROR",
                "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos.", null);
    }
}
