package com.ailabs.onboarding.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** JSON mal formado, tipos incorrectos o campos desconocidos: 400 INVALID_REQUEST. */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException exception) {
        return ErrorResponses.of(ApiException.badRequest(ErrorResponses.MALFORMED_MESSAGE));
    }
}
