package com.ailabs.onboarding.api;

import com.ailabs.onboarding.service.OnboardingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Estado de la wallet de la persona autenticada; lo consulta el punto de entrada de pagos (HU-02). */
@Path("/api/v1/wallets/me")
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {

    @Inject
    OnboardingService service;

    @GET
    public WalletResponse me(@HeaderParam(UserIdResolver.HEADER) String userHeader) {
        String userId = UserIdResolver.resolve(userHeader);
        return WalletResponse.from(service.wallet(userId));
    }
}
