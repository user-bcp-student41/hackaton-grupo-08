package com.ailabs.onboarding.api;

import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Red de seguridad: cualquier fallo inesperado (incluido el fallo al registrar un evento) responde
 * 500 INTERNAL_ERROR sin detalles internos y deja el detalle técnico solo en logs.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Error inesperado procesando la solicitud", exception);
        return ErrorResponses.of(ApiException.internal());
    }
}
