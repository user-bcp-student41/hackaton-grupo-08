package com.ailabs.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de analítica append-only. No contiene datos del formulario, solo identificadores.
 * {@code occurredAt} proviene del reloj de servidor (UTC).
 */
public record AnalyticsEvent(
        UUID eventId,
        EventType eventType,
        Instant occurredAt,
        String userId,
        UUID sessionId,
        String stepCode,
        String outcome,
        String acquisitionSource) {

    public static final String OUTCOME_COMPLETED = "COMPLETED";

    public static AnalyticsEvent started(OnboardingSession session, Instant at) {
        return new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_STARTED, at, session.userId(),
                session.id(), null, null, session.acquisitionSource());
    }

    public static AnalyticsEvent stepCompleted(OnboardingSession session, String stepCode, Instant at) {
        return new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_STEP_COMPLETED, at, session.userId(),
                session.id(), stepCode, null, session.acquisitionSource());
    }

    public static AnalyticsEvent closed(OnboardingSession session, Instant at) {
        return new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_CLOSED, at, session.userId(),
                session.id(), null, OUTCOME_COMPLETED, session.acquisitionSource());
    }
}
