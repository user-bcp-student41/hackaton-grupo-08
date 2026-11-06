package com.ailabs.onboarding.api;

import java.util.regex.Pattern;

/**
 * Sustituto de pruebas de la identidad (SUPUESTO S1: no existe mecanismo de autenticación en el repositorio).
 * Lee el identificador del header {@code X-User-Id}. NO es seguro para producción: cualquiera puede suplantar
 * un usuario. Debe reemplazarse por el mecanismo de autenticación real; el resto del módulo solo recibe el
 * {@code userId} resuelto.
 */
public final class UserIdResolver {

    public static final String HEADER = "X-User-Id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private UserIdResolver() {
    }

    public static String resolve(String headerValue) {
        if (headerValue == null || !VALID.matcher(headerValue.trim()).matches()) {
            throw ApiException.unauthenticated();
        }
        return headerValue.trim();
    }
}
