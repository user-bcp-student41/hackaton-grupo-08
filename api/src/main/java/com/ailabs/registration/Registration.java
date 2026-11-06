package com.ailabs.registration;

import java.time.Instant;
import java.util.UUID;

/** Registro/onboarding de 2 pasos: Paso 1 (PERSONAL_DATA) siempre completo al crearse; Paso 2 (OTP_VERIFICATION). */
public record Registration(UUID id, UUID customerId, String acquisitionSource, Instant startedAt,
        Instant completedAt) {

    public static final String STEP_PERSONAL_DATA = "PERSONAL_DATA";
    public static final String STEP_OTP = "OTP_VERIFICATION";

    public boolean completed() {
        return completedAt != null;
    }

    public Registration complete(Instant at) {
        return new Registration(id, customerId, acquisitionSource, startedAt, at);
    }
}
