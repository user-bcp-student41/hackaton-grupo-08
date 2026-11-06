package com.ailabs.onboarding.api;

import com.ailabs.onboarding.domain.OnboardingSession;
import com.ailabs.onboarding.service.CompletionResult;
import com.ailabs.onboarding.service.OnboardingService;
import com.ailabs.onboarding.service.StartResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** C1: autentica, valida la forma de la petición, verifica propiedad y delega en el servicio. */
@Path("/api/v1/onboarding/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class OnboardingResource {

    static final int MAX_DATA_FIELDS = 20;

    @Inject
    OnboardingService service;

    @ConfigProperty(name = "onboarding.payments-entry-point", defaultValue = "/payments/new")
    String paymentsEntryPoint;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(@HeaderParam(UserIdResolver.HEADER) String userHeader, StartSessionRequest body) {
        String userId = UserIdResolver.resolve(userHeader);
        StartResult result = service.start(userId, body == null ? null : body.acquisitionSource());
        return Response.status(result.created() ? 201 : 200).entity(SessionResponse.from(result.session())).build();
    }

    @GET
    @Path("/{sessionId}")
    public SessionResponse get(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @PathParam("sessionId") String sessionId) {
        String userId = UserIdResolver.resolve(userHeader);
        return SessionResponse.from(service.get(userId, parseSessionId(sessionId)));
    }

    @PUT
    @Path("/{sessionId}/steps/{stepCode}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionResponse submitStep(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @PathParam("sessionId") String sessionId, @PathParam("stepCode") String stepCode,
            StepSubmissionRequest body) {
        String userId = UserIdResolver.resolve(userHeader);
        UUID id = parseSessionId(sessionId);
        if (body == null || body.data() == null) {
            throw ApiException.badRequest("Envía los datos del paso en el campo data.");
        }
        Map<String, Object> data = body.data();
        if (data.size() > MAX_DATA_FIELDS) {
            throw ApiException.badRequest("Se enviaron demasiados campos en data.");
        }
        OnboardingSession session = service.submitStep(userId, id, stepCode, data);
        return SessionResponse.from(session);
    }

    @POST
    @Path("/{sessionId}/complete")
    public CompleteResponse complete(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @PathParam("sessionId") String sessionId) {
        String userId = UserIdResolver.resolve(userHeader);
        CompletionResult result = service.complete(userId, parseSessionId(sessionId));
        return new CompleteResponse(result.session().id(), result.session().status().name(),
                result.session().completedAt(), WalletResponse.from(result.wallet()), paymentsEntryPoint);
    }

    private static UUID parseSessionId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "No encontramos la sesión de onboarding.");
        }
    }
}
