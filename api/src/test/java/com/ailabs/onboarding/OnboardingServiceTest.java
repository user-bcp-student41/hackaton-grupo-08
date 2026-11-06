package com.ailabs.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.domain.EventType;
import com.ailabs.onboarding.domain.OnboardingSession;
import com.ailabs.onboarding.domain.StepStatus;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import com.ailabs.onboarding.persistence.InMemoryAnalyticsEventRepository;
import com.ailabs.onboarding.persistence.OnboardingStore;
import com.ailabs.onboarding.service.OnboardingMetricsService;
import com.ailabs.onboarding.service.OnboardingService;
import com.ailabs.onboarding.service.EventRecordingException;
import com.ailabs.onboarding.validation.MockKycStepValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OnboardingServiceTest {

    private static final Map<String, Object> DOCUMENT = Map.of("documentType", "TEST", "document", "TEST-001");
    private static final Map<String, Object> VERIFICATION = Map.of("scenario", "APPROVED");

    /** Repositorio que falla al registrar un tipo de evento, para simular CA-16. */
    private static final class FailingRepository extends InMemoryAnalyticsEventRepository {
        volatile EventType failOn;

        @Override
        public synchronized void append(AnalyticsEvent event) {
            if (event.eventType() == failOn) {
                throw new IllegalStateException("fallo simulado");
            }
            super.append(event);
        }
    }

    private static OnboardingService service(AnalyticsEventRepository repo, OnboardingStore store, Clock clock) {
        return new OnboardingService(new MockKycStepValidator(), repo, store, clock);
    }

    // CA-16
    @Test
    void failureRecordingStepEventRollsBackTheStepAndAllowsRetry() {
        FailingRepository repo = new FailingRepository();
        OnboardingService service = service(repo, new OnboardingStore(), Clock.systemUTC());
        OnboardingSession session = service.start("u1", null).session();

        repo.failOn = EventType.ONBOARDING_STEP_COMPLETED;
        assertThrows(EventRecordingException.class,
                () -> service.submitStep("u1", session.id(), "DOCUMENT", DOCUMENT));
        OnboardingSession after = service.get("u1", session.id());
        assertEquals("DOCUMENT", after.currentStepCode());
        assertEquals(StepStatus.IN_PROGRESS, after.step("DOCUMENT").orElseThrow().status());
        assertEquals(1, repo.findBySession(session.id()).size());

        repo.failOn = null;
        assertEquals("VERIFICATION", service.submitStep("u1", session.id(), "DOCUMENT", DOCUMENT).currentStepCode());
    }

    // CA-16
    @Test
    void failureRecordingCloseEventDoesNotActivateTheWallet() {
        FailingRepository repo = new FailingRepository();
        OnboardingService service = service(repo, new OnboardingStore(), Clock.systemUTC());
        OnboardingSession session = service.start("u1", null).session();
        service.submitStep("u1", session.id(), "DOCUMENT", DOCUMENT);
        service.submitStep("u1", session.id(), "VERIFICATION", VERIFICATION);

        repo.failOn = EventType.ONBOARDING_CLOSED;
        assertThrows(EventRecordingException.class, () -> service.complete("u1", session.id()));
        assertEquals("PENDING", service.wallet("u1").status().name());
        assertNull(service.get("u1", session.id()).completedAt());

        repo.failOn = null;
        assertEquals("ACTIVE", service.complete("u1", session.id()).wallet().status().name());
    }

    // CA-13
    @Test
    void parallelCompletionsProduceASingleCloseEvent() throws Exception {
        InMemoryAnalyticsEventRepository repo = new InMemoryAnalyticsEventRepository();
        OnboardingService service = service(repo, new OnboardingStore(), Clock.systemUTC());
        OnboardingSession session = service.start("u1", null).session();
        service.submitStep("u1", session.id(), "DOCUMENT", DOCUMENT);
        service.submitStep("u1", session.id(), "VERIFICATION", VERIFICATION);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Instant>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> service.complete("u1", session.id()).session().completedAt());
            }
            List<Instant> completedAt = new ArrayList<>();
            for (Future<Instant> future : pool.invokeAll(tasks)) {
                completedAt.add(future.get());
            }
            assertEquals(1, completedAt.stream().distinct().count());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, repo.findBySession(session.id()).stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_CLOSED).count());
    }

    // CA-15
    @Test
    void parallelSubmissionsOfTheSameStepProduceASingleStepEvent() throws Exception {
        InMemoryAnalyticsEventRepository repo = new InMemoryAnalyticsEventRepository();
        OnboardingService service = service(repo, new OnboardingStore(), Clock.systemUTC());
        OnboardingSession session = service.start("u1", null).session();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<OnboardingSession>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> service.submitStep("u1", session.id(), "DOCUMENT", DOCUMENT));
            }
            for (Future<OnboardingSession> future : pool.invokeAll(tasks)) {
                assertEquals("VERIFICATION", future.get().currentStepCode());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, repo.findBySession(session.id()).stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_STEP_COMPLETED).count());
    }

    @Test
    void attemptingToCompleteSomeoneElsesSessionIsForbidden() {
        OnboardingService service = service(new InMemoryAnalyticsEventRepository(), new OnboardingStore(),
                Clock.systemUTC());
        OnboardingSession session = service.start("u1", null).session();
        ApiException error = assertThrows(ApiException.class, () -> service.complete("u2", session.id()));
        assertEquals(403, error.status());
    }

    // HU1-AC5 / HU1-AC6 / CA-17: definiciones de analítica sobre eventos sintéticos
    @Test
    void metricsComputeMedianMonthlyNewActiveUsersAndUnattributedBucket() {
        InMemoryAnalyticsEventRepository repo = new InMemoryAnalyticsEventRepository();
        Instant t0 = Instant.parse("2026-11-30T23:00:00Z");
        addSession(repo, "a", "CAMPAIGN_X", t0, Duration.ofMinutes(2));
        addSession(repo, "b", null, t0, Duration.ofMinutes(4));
        addSession(repo, "c", "CAMPAIGN_X", t0.plus(Duration.ofHours(2)), Duration.ofMinutes(9));
        // sesión abandonada (solo inicio): fuera de la mediana, contada aparte
        repo.append(AnalyticsEvent.started(session("d", null, t0), t0));

        OnboardingMetricsService metrics = new OnboardingMetricsService(repo);
        OnboardingMetricsService.DurationReport report = metrics.durationReport(Instant.EPOCH,
                Instant.parse("2027-01-01T00:00:00Z"));
        assertEquals(3, report.completedSessions());
        assertEquals(1, report.sessionsWithoutCompletedClose());
        assertEquals(Duration.ofMinutes(4), report.median().orElseThrow());

        List<OnboardingMetricsService.MonthlyNewActiveUsers> monthly = metrics.newActiveUsersByMonth(ZoneOffset.UTC);
        assertTrue(monthly.contains(new OnboardingMetricsService.MonthlyNewActiveUsers(
                YearMonth.of(2026, 11), "CAMPAIGN_X", 1)));
        assertTrue(monthly.contains(new OnboardingMetricsService.MonthlyNewActiveUsers(
                YearMonth.of(2026, 11), "UNATTRIBUTED", 1)));
        assertTrue(monthly.contains(new OnboardingMetricsService.MonthlyNewActiveUsers(
                YearMonth.of(2026, 12), "CAMPAIGN_X", 1)));
    }

    private static OnboardingSession session(String user, String source, Instant startedAt) {
        return new OnboardingSession(UUID.randomUUID(), user, "w-" + user,
                com.ailabs.onboarding.domain.SessionStatus.IN_PROGRESS,
                source == null ? OnboardingService.UNATTRIBUTED : source, startedAt, null, 0, List.of());
    }

    private static void addSession(AnalyticsEventRepository repo, String user, String source, Instant start,
            Duration duration) {
        OnboardingSession session = session(user, source, start);
        repo.append(AnalyticsEvent.started(session, start));
        repo.append(AnalyticsEvent.closed(session, start.plus(duration)));
    }
}
