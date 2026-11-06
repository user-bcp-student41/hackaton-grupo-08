package com.ailabs.onboarding.service;

/** Fallo al registrar un evento de analítica; revierte la operación de negocio (decisión de arquitectura 4.6). */
public class EventRecordingException extends RuntimeException {

    public EventRecordingException(Throwable cause) {
        super("No se pudo registrar el evento de analítica", cause);
    }
}
