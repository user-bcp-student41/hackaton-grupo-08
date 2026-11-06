package com.ailabs.payments.api;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.payments.service.PaymentCommand;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cuerpo de E1/E2. {@code amount} llega como {@link JsonNode} para rechazar con {@code field=amount} los valores no
 * numéricos (el servidor no confía en el cliente). Campos desconocidos (p. ej. {@code collectionId}, fuera de
 * alcance) se rechazan por la configuración global de Jackson.
 */
public record PaymentRequest(String recipientRef, JsonNode amount, String currency, String providerId,
        String channel) {

    static final int MAX_RECIPIENT_REF = 64;
    static final BigDecimal MAX_AMOUNT_SANITY = new BigDecimal("1000000000");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final String CURRENCY = "PEN";

    /** Valida la forma y devuelve el comando; lanza 400 con {@code details.errors} si hay campos inválidos. */
    public static PaymentCommand toCommand(PaymentRequest request) {
        if (request == null) {
            throw ApiException.invalidFields(List.of(error("body", "REQUIRED")));
        }
        List<Map<String, String>> errors = new ArrayList<>();
        String recipient = request.recipientRef();
        if (recipient == null || recipient.isBlank()) {
            errors.add(error("recipientRef", "REQUIRED"));
        } else if (recipient.length() > MAX_RECIPIENT_REF || !recipient.equals(recipient.strip())
                || recipient.chars().anyMatch(Character::isISOControl)) {
            errors.add(error("recipientRef", "INVALID_FORMAT"));
        }
        BigDecimal amount = readAmount(request.amount(), errors);
        if (request.currency() == null || request.currency().isBlank()) {
            errors.add(error("currency", "REQUIRED"));
        } else if (!CURRENCY.equals(request.currency())) {
            errors.add(error("currency", "UNSUPPORTED"));
        }
        requireCode(request.providerId(), "providerId", errors);
        requireCode(request.channel(), "channel", errors);
        if (!errors.isEmpty()) {
            throw ApiException.invalidFields(errors);
        }
        return new PaymentCommand(recipient, amount, CURRENCY, request.providerId(), request.channel());
    }

    private static BigDecimal readAmount(JsonNode node, List<Map<String, String>> errors) {
        if (node == null || node.isNull()) {
            errors.add(error("amount", "REQUIRED"));
            return null;
        }
        if (!node.isNumber()) {
            errors.add(error("amount", "MUST_BE_NUMBER"));
            return null;
        }
        if (node.isDouble() || node.isFloat()) {
            double value = node.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                errors.add(error("amount", "MUST_BE_NUMBER"));
                return null;
            }
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() <= 0) {
            errors.add(error("amount", "MUST_BE_POSITIVE"));
            return null;
        }
        if (value.compareTo(MAX_AMOUNT_SANITY) > 0) {
            errors.add(error("amount", "TOO_LARGE"));
            return null;
        }
        if (value.stripTrailingZeros().scale() > 2) {
            errors.add(error("amount", "TOO_MANY_DECIMALS"));
            return null;
        }
        return value;
    }

    private static void requireCode(String value, String field, List<Map<String, String>> errors) {
        if (value == null || value.isBlank()) {
            errors.add(error(field, "REQUIRED"));
        } else if (!CODE.matcher(value).matches()) {
            errors.add(error(field, "INVALID_FORMAT"));
        }
    }

    private static Map<String, String> error(String field, String reason) {
        return Map.of("field", field, "reason", reason);
    }
}
