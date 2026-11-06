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

    // ---- HU-02 (pagos de bajo monto) ----

    /** 400 con {@code details.errors = [{field, reason}]} cuando se conocen los campos inválidos. */
    public static ApiException invalidFields(List<Map<String, String>> errors) {
        return new ApiException(400, "INVALID_REQUEST", "Revisa los datos enviados.", Map.of("errors", errors));
    }

    public static ApiException paymentNotFound() {
        return new ApiException(404, "PAYMENT_NOT_FOUND", "No encontramos este pago.", null);
    }

    public static ApiException recipientNotFound() {
        return new ApiException(404, "RECIPIENT_NOT_FOUND",
                "No encontramos al destinatario. Revisa el dato e inténtalo de nuevo.", null);
    }

    public static ApiException walletNotActive() {
        return new ApiException(409, "WALLET_NOT_ACTIVE",
                "Tu wallet aún no está activa. Completa el onboarding para pagar.", null);
    }

    public static ApiException idempotencyKeyConflict() {
        return new ApiException(409, "IDEMPOTENCY_KEY_CONFLICT",
                "Esta clave ya se usó con otra solicitud. Genera una nueva operación.", null);
    }

    public static ApiException paymentNotRetryable(String reason) {
        return new ApiException(409, "PAYMENT_NOT_RETRYABLE", "Este pago no se puede reintentar en este momento.",
                Map.of("reason", reason));
    }

    /** 422 sin exponer el valor del límite (pregunta abierta P6). */
    public static ApiException amountLimitExceeded() {
        return new ApiException(422, "AMOUNT_LIMIT_EXCEEDED",
                "El monto supera el máximo permitido para este canal.", Map.of("field", "amount"));
    }

    public static ApiException providerChannelUnsupported() {
        return new ApiException(422, "PROVIDER_CHANNEL_UNSUPPORTED",
                "El proveedor o canal elegido no está disponible para pagos.",
                Map.of("fields", List.of("providerId", "channel")));
    }

    public static ApiException providerUnavailable() {
        return new ApiException(503, "PROVIDER_UNAVAILABLE",
                "No pudimos comunicarnos con el proveedor. Inténtalo de nuevo.", null);
    }

    public static ApiException internal() {
        return new ApiException(500, "INTERNAL_ERROR",
                "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos.", null);
    }
}
