package com.ailabs.registration;

import com.ailabs.onboarding.api.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Resuelve la identidad desde {@code Authorization: Bearer}. Reemplaza a {@code UserIdResolver} en recursos
 * protegidos. El header {@code X-User-Id} solo se acepta con {@code auth.legacy-user-id-header=true} (perfiles
 * de prueba/desarrollo; nunca en producción).
 */
@ApplicationScoped
public class AuthenticatedUserResolver {

    private static final Pattern BEARER = Pattern.compile("Bearer ([A-Za-z0-9_-]{20,128})");
    private static final Pattern LEGACY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Inject
    AuthService auth;

    @ConfigProperty(name = "auth.legacy-user-id-header", defaultValue = "false")
    boolean legacyHeader;

    public static Optional<String> bearerToken(String authorization) {
        if (authorization == null) {
            return Optional.empty();
        }
        Matcher m = BEARER.matcher(authorization.trim());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public String resolve(String authorization, String legacyUserId) {
        Optional<String> token = bearerToken(authorization);
        if (token.isPresent()) {
            return auth.authenticate(token.get()).orElseThrow(ApiException::unauthenticated);
        }
        if (legacyHeader && legacyUserId != null && LEGACY.matcher(legacyUserId.trim()).matches()) {
            return legacyUserId.trim();
        }
        throw ApiException.unauthenticated();
    }
}
