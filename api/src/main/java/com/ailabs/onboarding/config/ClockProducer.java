package com.ailabs.onboarding.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Reloj de servidor en UTC; todos los timestamps de eventos y sesiones salen de aquí, nunca del cliente. */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
