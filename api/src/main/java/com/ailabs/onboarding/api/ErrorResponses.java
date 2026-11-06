package com.ailabs.onboarding.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

final class ErrorResponses {

    static final String MALFORMED_MESSAGE = "La solicitud no tiene un formato válido. Revisa los datos enviados.";

    private ErrorResponses() {
    }

    static Response of(ApiException exception) {
        return Response.status(exception.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(exception.code(), exception.getMessage(), exception.details()))
                .build();
    }
}
