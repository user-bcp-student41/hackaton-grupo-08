package com.ailabs.onboarding.api;

import com.ailabs.onboarding.service.OnboardingService;
import com.ailabs.registration.AuthenticatedUserResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Estado de la wallet de la persona autenticada; lo consulta el punto de entrada de pagos (HU-02).
 * Exige {@code Authorization: Bearer} (sesión de HU-01); {@code X-User-Id} solo en perfiles de prueba.
 */
@Path("/api/v1/wallets/me")
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {

    @Inject
    OnboardingService service;

    @Inject
    AuthenticatedUserResolver resolver;

    @GET
    public WalletResponse me(@HeaderParam("Authorization") String authorization,
            @HeaderParam(UserIdResolver.HEADER) String userHeader) {
        String userId = resolver.resolve(authorization, userHeader);
        return WalletResponse.from(service.wallet(userId));
    }
}
