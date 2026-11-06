package com.ailabs.registration;

import java.util.List;
import java.util.regex.Pattern;

/** Reglas de campo compartidas por registro y login. No normaliza ni recorta valores. */
public final class FieldRules {

    private static final Pattern DOCUMENT = Pattern.compile("[0-9]{8}");
    private static final Pattern PASSWORD = Pattern.compile("[0-9]{6}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");
    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    static final int MAX_EMAIL = 120;

    private FieldRules() {
    }

    public static void documentType(String value, List<FieldIssue> out) {
        if (value == null || value.isEmpty()) {
            out.add(new FieldIssue("documentType", "REQUIRED", "Selecciona el tipo de documento (DNI o CE)."));
        } else if (!value.equals("DNI") && !value.equals("CE")) {
            out.add(new FieldIssue("documentType", "UNSUPPORTED_VALUE", "El tipo de documento debe ser DNI o CE."));
        }
    }

    public static void documentNumber(String value, List<FieldIssue> out) {
        if (value == null || value.isEmpty()) {
            out.add(new FieldIssue("documentNumber", "REQUIRED", "Ingresa tu número de documento de 8 dígitos."));
        } else if (!DOCUMENT.matcher(value).matches()) {
            out.add(new FieldIssue("documentNumber", "INVALID_FORMAT",
                    "El documento debe tener exactamente 8 dígitos, sin letras, espacios ni símbolos."));
        }
    }

    public static void password(String value, List<FieldIssue> out) {
        if (value == null || value.isEmpty()) {
            out.add(new FieldIssue("password", "REQUIRED", "Ingresa una contraseña de 6 dígitos."));
        } else if (!PASSWORD.matcher(value).matches()) {
            out.add(new FieldIssue("password", "INVALID_LENGTH", "La contraseña debe tener exactamente 6 dígitos."));
        }
    }

    public static void email(String value, List<FieldIssue> out) {
        if (value == null || value.isEmpty()) {
            out.add(new FieldIssue("email", "REQUIRED", "Ingresa tu correo electrónico."));
        } else if (value.length() > MAX_EMAIL || !EMAIL.matcher(value).matches()) {
            out.add(new FieldIssue("email", "INVALID_FORMAT", "Ingresa un correo válido, por ejemplo nombre@dominio.com."));
        }
    }

    public static boolean validSource(String value) {
        return value == null || SOURCE.matcher(value).matches();
    }
}
