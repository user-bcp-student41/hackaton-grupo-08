package com.ailabs.onboarding.persistence;

import com.ailabs.onboarding.domain.AnalyticsEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación en memoria (NO durable). Sustituye a la tabla {@code analytics_event} hasta que Datos defina
 * el motor de base de datos (ver developer_spec: pendientes).
 */
@ApplicationScoped
public class InMemoryAnalyticsEventRepository implements AnalyticsEventRepository {

    private final List<AnalyticsEvent> events = new ArrayList<>();
    private final Set<UUID> eventIds = new HashSet<>();
    private final Set<String> naturalKeys = new HashSet<>();

    @Override
    public synchronized void append(AnalyticsEvent event) {
        String naturalKey = event.sessionId() + "|" + event.eventType() + "|" + event.stepCode();
        if (eventIds.contains(event.eventId()) || naturalKeys.contains(naturalKey)) {
            throw new IllegalStateException("Evento duplicado");
        }
        events.add(event);
        eventIds.add(event.eventId());
        naturalKeys.add(naturalKey);
    }

    @Override
    public synchronized List<AnalyticsEvent> findAll() {
        return List.copyOf(events);
    }

    @Override
    public synchronized List<AnalyticsEvent> findBySession(UUID sessionId) {
        return events.stream().filter(event -> sessionId.equals(event.sessionId())).toList();
    }
}
