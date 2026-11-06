package com.ailabs.registration;

import com.ailabs.registration.RegistrationResponses.LoginView;
import com.ailabs.registration.RegistrationResponses.RegistrationView;
import com.ailabs.registration.RegistrationResponses.VerifyView;
import com.ailabs.onboarding.api.ApiException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Rutas públicas de registro y autenticación. Sin reglas de negocio: valida forma y delega. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class RegistrationResource {

    @Inject
    RegistrationService registrations;
    @Inject
    AuthService auth;

    @POST
    @Path("/registrations")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegisterRequest body) {
        RegistrationView view = registrations.register(body);
        return Response.status(201).entity(view).build();
    }

    @GET
    @Path("/registrations/{id}")
    public RegistrationView get(@PathParam("id") String id) {
        return registrations.get(parse(id));
    }

    @POST
    @Path("/registrations/{id}/otp/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public VerifyView verify(@PathParam("id") String id, VerifyOtpRequest body) {
        return registrations.verify(parse(id), body);
    }

    @POST
    @Path("/registrations/{id}/otp/resend")
    public Response resend(@PathParam("id") String id) {
        registrations.resend(parse(id));
        return Response.accepted().build();
    }

    @POST
    @Path("/auth/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginView login(LoginRequest body) {
        return auth.login(body);
    }

    @POST
    @Path("/auth/logout")
    public Response logout(@HeaderParam("Authorization") String authorization) {
        String token = AuthenticatedUserResolver.bearerToken(authorization)
                .orElseThrow(ApiException::unauthenticated);
        auth.logout(token);
        return Response.noContent().build();
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw RegistrationErrors.registrationNotFound();
        }
    }
}
