package com.ailabs.onboarding.validation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementación en proceso que reproduce el contrato KYC del mock (docs/bcp-wallet-mock-openapi.yaml,
 * POST /kyc/verifications): {@code documentType} solo TEST, {@code document} con prefijo {@code TEST-} y
 * longitud 1..200, y {@code scenario} APPROVED | REJECTED | MANUAL_REVIEW | UNAVAILABLE (por defecto APPROVED).
 *
 * <p>SUPUESTO: el reparto en dos pasos (DOCUMENT, VERIFICATION) es una decisión de este adaptador, porque el
 * mock expone una sola operación. No se definen requisitos de KYC propios. No se llama al mock por HTTP.
 * Nunca se incluyen los valores recibidos en mensajes ni en logs.
 */
@ApplicationScoped
public class MockKycStepValidator implements StepValidator {

    public static final String STEP_DOCUMENT = "DOCUMENT";
    public static final String STEP_VERIFICATION = "VERIFICATION";

    static final int DOCUMENT_MAX_LENGTH = 200;
    static final String DOCUMENT_PREFIX = "TEST-";
    static final String DOCUMENT_TYPE_TEST = "TEST";
    static final Set<String> SCENARIOS = Set.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "UNAVAILABLE");

    private static final List<StepDefinition> CATALOG = List.of(
            new StepDefinition(STEP_DOCUMENT, 1),
            new StepDefinition(STEP_VERIFICATION, 2));

    @Override
    public List<StepDefinition> catalog() {
        return CATALOG;
    }

    @Override
    public List<FieldError> validate(String stepCode, Map<String, Object> data) {
        return switch (stepCode) {
            case STEP_DOCUMENT -> validateDocument(data);
            case STEP_VERIFICATION -> validateVerification(data);
            default -> throw new IllegalArgumentException("Paso desconocido");
        };
    }

    private List<FieldError> validateDocument(Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();
        rejectUnknownFields(data, Set.of("documentType", "document"), errors);

        String documentType = readText(data, "documentType", errors, "Selecciona el tipo de documento.");
        if (documentType != null && !DOCUMENT_TYPE_TEST.equals(documentType)) {
            errors.add(new FieldError("documentType", "INVALID_VALUE", "El tipo de documento no es válido.",
                    "Selecciona el tipo de documento TEST (solo entorno de prueba)."));
        }

        String document = readText(data, "document", errors, "Ingresa el número de documento de prueba.");
        if (document != null) {
            if (document.length() > DOCUMENT_MAX_LENGTH) {
                errors.add(new FieldError("document", "TOO_LONG", "El documento es demasiado largo.",
                        "Ingresa como máximo " + DOCUMENT_MAX_LENGTH + " caracteres."));
            } else if (!document.startsWith(DOCUMENT_PREFIX)) {
                errors.add(new FieldError("document", "INVALID_FORMAT", "El formato del documento no es válido.",
                        "Ingresa el documento de prueba con el formato TEST-xxx (debe empezar con TEST-)."));
            }
        }
        return errors;
    }

    private List<FieldError> validateVerification(Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();
        rejectUnknownFields(data, Set.of("scenario"), errors);

        Object raw = data.get("scenario");
        String scenario = "APPROVED";
        if (raw != null) {
            if (!(raw instanceof String text)) {
                errors.add(new FieldError("scenario", "INVALID_TYPE", "El escenario debe ser texto.",
                        "Selecciona uno de los escenarios disponibles."));
                return errors;
            }
            if (!text.isBlank()) {
                scenario = text;
            }
        }
        if (!SCENARIOS.contains(scenario)) {
            errors.add(new FieldError("scenario", "INVALID_VALUE", "El escenario no es válido.",
                    "Selecciona uno de los escenarios disponibles: APPROVED, REJECTED, MANUAL_REVIEW o UNAVAILABLE."));
            return errors;
        }
        switch (scenario) {
            case "UNAVAILABLE" -> throw new ValidationUnavailableException("Proveedor KYC simulado no disponible");
            case "REJECTED" -> errors.add(new FieldError("scenario", "KYC_REJECTED",
                    "La verificación fue rechazada.",
                    "Cambia el escenario a APPROVED (entorno de prueba) y vuelve a continuar."));
            case "MANUAL_REVIEW" -> errors.add(new FieldError("scenario", "KYC_MANUAL_REVIEW",
                    "La verificación requiere revisión manual y aún no puede continuar.",
                    "Cambia el escenario a APPROVED (entorno de prueba) y vuelve a continuar."));
            default -> {
                // APPROVED: sin errores
            }
        }
        return errors;
    }

    private static void rejectUnknownFields(Map<String, Object> data, Set<String> allowed, List<FieldError> errors) {
        data.keySet().stream().sorted().filter(key -> !allowed.contains(key)).forEach(key ->
                errors.add(new FieldError(safeFieldName(key), "UNKNOWN_FIELD", "Este dato no corresponde a este paso.",
                        "Elimina el campo y vuelve a intentarlo.")));
    }

    /** Evita reflejar nombres de campo arbitrarios o excesivamente largos en la respuesta. */
    private static String safeFieldName(String key) {
        return key.length() <= 50 && key.matches("[A-Za-z0-9_.-]+") ? key : "(campo desconocido)";
    }

    private static String readText(Map<String, Object> data, String field, List<FieldError> errors,
            String requiredHowToFix) {
        Object value = data.get(field);
        if (value == null || (value instanceof String text && text.isBlank())) {
            errors.add(new FieldError(field, "REQUIRED", "Este dato es obligatorio.", requiredHowToFix));
            return null;
        }
        if (!(value instanceof String text)) {
            errors.add(new FieldError(field, "INVALID_TYPE", "El dato debe ser texto.",
                    "Ingresa el dato como texto."));
            return null;
        }
        return text;
    }
}
