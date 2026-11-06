package com.ailabs.payments.api;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.api.UserIdResolver;
import com.ailabs.payments.domain.CheckoutEventType;
import com.ailabs.payments.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/** E6: hitos de UI que el servidor no ve (pantalla de confirmación vista, "procesando" mostrado). */
@Path("/api/v1/checkout-events")
@Produces(MediaType.APPLICATION_JSON)
public class CheckoutEventsResource {

    @Inject
    PaymentService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response record(@HeaderParam(UserIdResolver.HEADER) String userHeader, CheckoutEventRequest body) {
        String userId = UserIdResolver.resolve(userHeader);
        if (body == null || body.eventType() == null) {
            throw ApiException.invalidFields(List.of(Map.of("field", "eventType", "reason", "REQUIRED")));
        }
        CheckoutEventType type;
        try {
            type = CheckoutEventType.valueOf(body.eventType());
        } catch (IllegalArgumentException invalid) {
            throw ApiException.invalidFields(List.of(Map.of("field", "eventType", "reason", "INVALID_VALUE")));
        }
        service.recordUiEvent(userId, type, body.paymentId());
        return Response.accepted().build();
    }
}
