package com.ailabs.onboarding.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Los errores 400 generados por el framework (p. ej. JSON con tipos incorrectos o campos desconocidos) se
 * traducen al formato común. El resto de {@link WebApplicationException} (404 de ruta, 405, 415...) conserva su
 * respuesta original.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        if (original.getStatus() == 400) {
            return ErrorResponses.of(ApiException.badRequest(ErrorResponses.MALFORMED_MESSAGE));
        }
        return original;
    }
}
