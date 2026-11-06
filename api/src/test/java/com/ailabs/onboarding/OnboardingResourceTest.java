package com.ailabs.onboarding;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.domain.EventType;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import com.ailabs.onboarding.service.OnboardingMetricsService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnboardingResourceTest {

    private static final String SESSIONS = "/api/v1/onboarding/sessions";

    @Inject
    AnalyticsEventRepository events;

    @Inject
    OnboardingMetricsService metrics;

    private static String newUser() {
        return "user-" + UUID.randomUUID();
    }

    private static ValidatableResponse start(String user, String body) {
        return given().header("X-User-Id", user).contentType(ContentType.JSON).body(body)
                .when().post(SESSIONS).then();
    }

    private static ValidatableResponse put(String user, String sessionId, String step, String body) {
        return given().header("X-User-Id", user).contentType(ContentType.JSON).body(body)
                .when().put(SESSIONS + "/" + sessionId + "/steps/" + step).then();
    }

    private static String documentBody(String document) {
        return "{\"data\":{\"documentType\":\"TEST\",\"document\":\"" + document + "\"}}";
    }

    private static String startSession(String user) {
        return start(user, "{}").statusCode(201).extract().path("sessionId");
    }

    // HU1-AC1
    @Test
    void startReturnsAllStepsAndCurrentStep() {
        start(newUser(), "{\"acquisitionSource\":\"CAMPAIGN_DEMO\"}")
                .statusCode(201)
                .body("sessionId", notNullValue())
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentStepCode", equalTo("DOCUMENT"))
                .body("readyToComplete", equalTo(false))
                .body("completedAt", nullValue())
                .body("steps.stepCode", contains("DOCUMENT", "VERIFICATION"))
                .body("steps.order", contains(1, 2))
                .body("steps.status", contains("IN_PROGRESS", "PENDING"));
    }

    // HU1-AC1 + CA-7
    @Test
    void stepAdvancesAndSessionResumesWithoutSecondStartedEvent() {
        String user = newUser();
        String sessionId = startSession(user);

        put(user, sessionId, "DOCUMENT", documentBody("TEST-001"))
                .statusCode(200)
                .body("currentStepCode", equalTo("VERIFICATION"))
                .body("steps.status", contains("COMPLETED", "IN_PROGRESS"));

        start(user, "{}")
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("currentStepCode", equalTo("VERIFICATION"));

        long started = events.findBySession(UUID.fromString(sessionId)).stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_STARTED).count();
        assertEquals(1, started);
    }

    // HU1-AC2
    @Test
    void missingRequiredFieldIsBlockedWithActionableMessage() {
        String user = newUser();
        String sessionId = startSession(user);

        put(user, sessionId, "DOCUMENT", "{\"data\":{\"documentType\":\"TEST\"}}")
                .statusCode(422)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("details.currentStepCode", equalTo("DOCUMENT"))
                .body("details.errors[0].field", equalTo("document"))
                .body("details.errors[0].code", equalTo("REQUIRED"))
                .body("details.errors[0].message", notNullValue())
                .body("details.errors[0].howToFix", notNullValue());

        given().header("X-User-Id", user).when().get(SESSIONS + "/" + sessionId).then()
                .statusCode(200)
                .body("currentStepCode", equalTo("DOCUMENT"))
                .body("steps.status", contains("IN_PROGRESS", "PENDING"));
        assertEquals(1, events.findBySession(UUID.fromString(sessionId)).size());
    }

    // HU1-AC2 + CA-10
    @Test
    void invalidFormatIsRejectedAndLengthBoundaryIsRespected() {
        String user = newUser();
        String sessionId = startSession(user);

        put(user, sessionId, "DOCUMENT", documentBody("12345678"))
                .statusCode(422)
                .body("details.errors[0].code", equalTo("INVALID_FORMAT"))
                .body("details.errors[0].howToFix", not(equalTo("12345678")));

        put(user, sessionId, "DOCUMENT", documentBody("TEST-" + "a".repeat(196)))
                .statusCode(422)
                .body("details.errors[0].code", equalTo("TOO_LONG"));

        put(user, sessionId, "DOCUMENT", documentBody("TEST-" + "a".repeat(195)))
                .statusCode(200);
    }

    // CA-9
    @Test
    void outOfOrderStepIsConflictWithoutChanges() {
        String user = newUser();
        String sessionId = startSession(user);

        put(user, sessionId, "VERIFICATION", "{\"data\":{\"scenario\":\"APPROVED\"}}")
                .statusCode(409)
                .body("code", equalTo("STEP_OUT_OF_ORDER"))
                .body("details.currentStepCode", equalTo("DOCUMENT"));
        assertEquals(1, events.findBySession(UUID.fromString(sessionId)).size());
    }

    @Test
    void unknownStepIsNotFound() {
        String user = newUser();
        String sessionId = startSession(user);
        put(user, sessionId, "NOPE", "{\"data\":{}}").statusCode(404).body("code", equalTo("STEP_NOT_FOUND"));
    }

    // CA-11
    @Test
    void validatorUnavailableReturns503AndStepCanBeRetried() {
        String user = newUser();
        String sessionId = startSession(user);
        put(user, sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(200);

        put(user, sessionId, "VERIFICATION", "{\"data\":{\"scenario\":\"UNAVAILABLE\"}}")
                .statusCode(503)
                .body("code", equalTo("VALIDATION_UNAVAILABLE"))
                .body("message", equalTo("No pudimos validar tu información, inténtalo de nuevo."));
        assertEquals(2, events.findBySession(UUID.fromString(sessionId)).size());

        put(user, sessionId, "VERIFICATION", "{\"data\":{\"scenario\":\"APPROVED\"}}").statusCode(200);
    }

    // CA-12
    @Test
    void completeWithPendingStepsIsConflict() {
        String user = newUser();
        String sessionId = startSession(user);
        put(user, sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(200);

        given().header("X-User-Id", user).when().post(SESSIONS + "/" + sessionId + "/complete").then()
                .statusCode(409)
                .body("code", equalTo("STEPS_INCOMPLETE"))
                .body("details.pendingSteps", contains("VERIFICATION"));
        given().header("X-User-Id", user).when().get("/api/v1/wallets/me").then()
                .statusCode(200).body("status", equalTo("PENDING"));
    }

    // HU1-AC3, HU1-AC4, CA-8, CA-13, CA-15
    @Test
    void fullOnboardingActivatesWalletRecordsEventsAndIsIdempotent() {
        String user = newUser();
        String sessionId = startSession(user);
        put(user, sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(200);
        put(user, sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(200)
                .body("steps.status", contains("COMPLETED", "IN_PROGRESS"));
        put(user, sessionId, "VERIFICATION", "{\"data\":{}}")
                .statusCode(200)
                .body("currentStepCode", nullValue())
                .body("readyToComplete", equalTo(true));

        String completedAt = given().header("X-User-Id", user).when()
                .post(SESSIONS + "/" + sessionId + "/complete").then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("wallet.status", equalTo("ACTIVE"))
                .body("wallet.walletId", notNullValue())
                .body("paymentsEntryPoint", equalTo("/payments/new"))
                .extract().path("completedAt");

        given().header("X-User-Id", user).when().post(SESSIONS + "/" + sessionId + "/complete").then()
                .statusCode(200)
                .body("completedAt", equalTo(completedAt))
                .body("wallet.status", equalTo("ACTIVE"));

        given().header("X-User-Id", user).when().get("/api/v1/wallets/me").then()
                .statusCode(200).body("status", equalTo("ACTIVE"));

        start(user, "{}").statusCode(409).body("code", equalTo("ONBOARDING_ALREADY_COMPLETED"));

        List<AnalyticsEvent> sessionEvents = events.findBySession(UUID.fromString(sessionId));
        assertEquals(1, sessionEvents.stream().filter(e -> e.eventType() == EventType.ONBOARDING_STARTED).count());
        assertEquals(List.of("DOCUMENT", "VERIFICATION"), sessionEvents.stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_STEP_COMPLETED).map(AnalyticsEvent::stepCode)
                .toList());
        List<AnalyticsEvent> closes = sessionEvents.stream()
                .filter(e -> e.eventType() == EventType.ONBOARDING_CLOSED).toList();
        assertEquals(1, closes.size());
        assertEquals("COMPLETED", closes.get(0).outcome());
        sessionEvents.forEach(e -> {
            assertNotNull(e.eventId());
            assertEquals(user, e.userId());
            assertNotNull(e.occurredAt());
        });
        assertEquals(4, sessionEvents.size());

        // HU1-AC5 / HU1-AC6: la capacidad de medir existe (sin afirmar resultados de negocio)
        assertTrue(metrics.sessionDurations().stream().anyMatch(d -> d.sessionId().toString().equals(sessionId)));
        assertTrue(metrics.newActiveUsersByMonth(ZoneOffset.UTC).stream()
                .anyMatch(m -> m.acquisitionSource().equals("UNATTRIBUTED") && m.newActiveUsers() >= 1));
        assertFalse(metrics.durationReport(Instant.EPOCH, Instant.now().plusSeconds(60)).median().isEmpty());
    }

    private static void assertNotNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value);
    }

    // CA-14 (lado HU-01): sin onboarding no hay wallet
    @Test
    void walletMeIsNotFoundBeforeOnboardingAndRequiresIdentity() {
        given().header("X-User-Id", newUser()).when().get("/api/v1/wallets/me").then()
                .statusCode(404).body("code", equalTo("WALLET_NOT_FOUND"));
        given().when().get("/api/v1/wallets/me").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void requestsWithoutIdentityAreUnauthorized() {
        given().contentType(ContentType.JSON).body("{}").when().post(SESSIONS).then()
                .statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void sessionsOfOtherUsersAreForbiddenAndUnknownOnesNotFound() {
        String owner = newUser();
        String sessionId = startSession(owner);

        given().header("X-User-Id", newUser()).when().get(SESSIONS + "/" + sessionId).then()
                .statusCode(403).body("code", equalTo("SESSION_FORBIDDEN"));
        put(newUser(), sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(403);
        given().header("X-User-Id", owner).when().get(SESSIONS + "/" + UUID.randomUUID()).then()
                .statusCode(404).body("code", equalTo("SESSION_NOT_FOUND"));
        given().header("X-User-Id", owner).when().get(SESSIONS + "/not-a-uuid").then()
                .statusCode(404).body("code", equalTo("SESSION_NOT_FOUND"));
    }

    @Test
    void malformedRequestsAreBadRequests() {
        String user = newUser();
        String sessionId = startSession(user);

        put(user, sessionId, "DOCUMENT", "{}").statusCode(400).body("code", equalTo("INVALID_REQUEST"));
        put(user, sessionId, "DOCUMENT", "{\"data\":\"texto\"}").statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
        put(user, sessionId, "DOCUMENT", "{not json").statusCode(400).body("code", equalTo("INVALID_REQUEST"));
        put(user, sessionId, "DOCUMENT", "{\"data\":{},\"extra\":1}").statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
        start(newUser(), "{\"acquisitionSource\":\"bad source!\"}").statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
    }

    @Test
    void unknownFieldsInStepDataAreRejectedWithoutEchoingValues() {
        String user = newUser();
        String sessionId = startSession(user);
        Map<String, Object> payload = Map.of("data",
                Map.of("documentType", "TEST", "document", "TEST-001", "secret", "valor-sensible"));
        given().header("X-User-Id", user).contentType(ContentType.JSON).body(payload)
                .when().put(SESSIONS + "/" + sessionId + "/steps/DOCUMENT").then()
                .statusCode(422)
                .body("details.errors[0].code", equalTo("UNKNOWN_FIELD"))
                .body("details.errors", hasSize(1))
                .body("details.errors[0].message", not(org.hamcrest.Matchers.containsString("valor-sensible")));
    }

    @Test
    void rejectedScenarioBlocksTheLastStep() {
        String user = newUser();
        String sessionId = startSession(user);
        put(user, sessionId, "DOCUMENT", documentBody("TEST-001")).statusCode(200);
        put(user, sessionId, "VERIFICATION", "{\"data\":{\"scenario\":\"REJECTED\"}}")
                .statusCode(422)
                .body("details.errors[0].code", equalTo("KYC_REJECTED"))
                .body("details.errors", not(empty()));
    }
}
