package com.ailabs.onboarding.service;

import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.domain.EventType;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Consultas analíticas sobre los eventos (C7) para HU1-AC5 y HU1-AC6. Todas las definiciones son PROPUESTAS,
 * no aprobadas por Producto:
 * <ul>
 *   <li>Duración = ONBOARDING_CLOSED(COMPLETED).occurredAt - ONBOARDING_STARTED.occurredAt.</li>
 *   <li>Nuevo usuario activo del mes = userId con su primer ONBOARDING_CLOSED(COMPLETED) en ese mes (UTC por
 *       defecto, mes asignado por fecha de cierre).</li>
 *   <li>El CAC NO se calcula: está bloqueado hasta confirmar la fuente de gasto de adquisición.</li>
 * </ul>
 * No expone endpoint HTTP (decisión de arquitectura). No afirma resultados reales de negocio.
 */
@ApplicationScoped
public class OnboardingMetricsService {

    public record SessionDuration(UUID sessionId, String userId, String acquisitionSource, Instant startedAt,
            Instant closedAt, Duration duration) {
    }

    public record DurationReport(long completedSessions, long sessionsWithoutCompletedClose,
            Optional<Duration> median) {
    }

    public record MonthlyNewActiveUsers(YearMonth month, String acquisitionSource, long newActiveUsers) {
    }

    private final AnalyticsEventRepository events;

    @Inject
    public OnboardingMetricsService(AnalyticsEventRepository events) {
        this.events = events;
    }

    /** Duración por sesión con inicio y cierre COMPLETED. Las sesiones sin cierre quedan fuera. */
    public List<SessionDuration> sessionDurations() {
        return durations(events.findAll());
    }

    /**
     * Mediana de duración de las sesiones cerradas en [from, to) (por fecha de cierre); cuenta aparte las sesiones
     * iniciadas sin cierre COMPLETED.
     */
    public DurationReport durationReport(Instant from, Instant to) {
        List<AnalyticsEvent> all = events.findAll();
        List<SessionDuration> inRange = durations(all).stream()
                .filter(d -> !d.closedAt().isBefore(from) && d.closedAt().isBefore(to))
                .toList();
        Set<UUID> closed = new HashSet<>();
        all.stream().filter(OnboardingMetricsService::isCompletedClose).forEach(e -> closed.add(e.sessionId()));
        long withoutClose = all.stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_STARTED)
                .filter(e -> !closed.contains(e.sessionId()))
                .count();
        return new DurationReport(inRange.size(), withoutClose, median(inRange));
    }

    /** Nuevos usuarios activos por mes calendario y por origen de adquisición ({@code UNATTRIBUTED} si falta). */
    public List<MonthlyNewActiveUsers> newActiveUsersByMonth(ZoneId zone) {
        ZoneId effectiveZone = zone == null ? ZoneOffset.UTC : zone;
        Map<String, AnalyticsEvent> firstCloseByUser = new HashMap<>();
        for (AnalyticsEvent event : events.findAll()) {
            if (isCompletedClose(event)) {
                firstCloseByUser.merge(event.userId(), event,
                        (a, b) -> b.occurredAt().isBefore(a.occurredAt()) ? b : a);
            }
        }
        Map<YearMonth, Map<String, Long>> counts = new TreeMap<>();
        for (AnalyticsEvent close : firstCloseByUser.values()) {
            YearMonth month = YearMonth.from(close.occurredAt().atZone(effectiveZone));
            String source = close.acquisitionSource() == null ? OnboardingService.UNATTRIBUTED
                    : close.acquisitionSource();
            counts.computeIfAbsent(month, m -> new TreeMap<>()).merge(source, 1L, Long::sum);
        }
        List<MonthlyNewActiveUsers> result = new ArrayList<>();
        counts.forEach((month, bySource) -> bySource.forEach(
                (source, count) -> result.add(new MonthlyNewActiveUsers(month, source, count))));
        return result;
    }

    private static List<SessionDuration> durations(List<AnalyticsEvent> all) {
        Map<UUID, AnalyticsEvent> started = new HashMap<>();
        all.stream().filter(e -> e.eventType() == EventType.ONBOARDING_STARTED)
                .forEach(e -> started.put(e.sessionId(), e));
        List<SessionDuration> result = new ArrayList<>();
        for (AnalyticsEvent close : all) {
            AnalyticsEvent start = started.get(close.sessionId());
            if (isCompletedClose(close) && start != null) {
                result.add(new SessionDuration(close.sessionId(), close.userId(), close.acquisitionSource(),
                        start.occurredAt(), close.occurredAt(),
                        Duration.between(start.occurredAt(), close.occurredAt())));
            }
        }
        return result;
    }

    private static Optional<Duration> median(List<SessionDuration> durations) {
        if (durations.isEmpty()) {
            return Optional.empty();
        }
        List<Duration> sorted = durations.stream().map(SessionDuration::duration)
                .sorted(Comparator.naturalOrder()).toList();
        int size = sorted.size();
        if (size % 2 == 1) {
            return Optional.of(sorted.get(size / 2));
        }
        Duration lower = sorted.get(size / 2 - 1);
        Duration upper = sorted.get(size / 2);
        return Optional.of(lower.plus(upper.minus(lower).dividedBy(2)));
    }

    private static boolean isCompletedClose(AnalyticsEvent event) {
        return event.eventType() == EventType.ONBOARDING_CLOSED
                && AnalyticsEvent.OUTCOME_COMPLETED.equals(event.outcome());
    }
}
