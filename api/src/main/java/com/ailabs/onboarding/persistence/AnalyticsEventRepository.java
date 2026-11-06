package com.ailabs.onboarding.persistence;

import com.ailabs.onboarding.domain.AnalyticsEvent;
import java.util.List;

/** Almacén append-only de eventos de analítica. */
public interface AnalyticsEventRepository {

    /**
     * Agrega un evento. Garantiza la unicidad de {@code eventId} y de (sessionId, eventType, stepCode).
     *
     * @throws IllegalStateException si el evento duplica uno ya registrado.
     */
    void append(AnalyticsEvent event);

    List<AnalyticsEvent> findAll();

    List<AnalyticsEvent> findBySession(java.util.UUID sessionId);
}
