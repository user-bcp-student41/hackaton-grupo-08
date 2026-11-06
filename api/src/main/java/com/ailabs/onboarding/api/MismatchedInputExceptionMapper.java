package com.ailabs.onboarding.api;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Tipos incorrectos o campos desconocidos en el JSON (p. ej. {@code data} que no es un objeto): 400
 * INVALID_REQUEST. Se declara con prioridad alta para imponerse al mapper por defecto de Quarkus, que devuelve
 * otro formato de cuerpo.
 */
@Provider
@Priority(1)
public class MismatchedInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return ErrorResponses.of(ApiException.badRequest(ErrorResponses.MALFORMED_MESSAGE));
    }
}
