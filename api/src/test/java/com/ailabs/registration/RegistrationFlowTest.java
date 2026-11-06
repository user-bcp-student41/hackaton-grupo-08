package com.ailabs.registration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RegistrationFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger(10_000);

    @Inject
    SimulatedOtpSender mailbox;
    @Inject
    AnalyticsEventRepository events;

    private static String newDoc() {
        return String.format("%08d", SEQ.incrementAndGet());
    }

    private static String body(String type, String doc, String email, String pwd) {
        return "{\"documentType\":\"" + type + "\",\"documentNumber\":\"" + doc + "\",\"email\":\"" + email
                + "\",\"password\":\"" + pwd + "\",\"acquisitionSource\":\"demo\"}";
    }

    private Response register(String doc, String email) {
        return given().contentType(ContentType.JSON).body(body("DNI", doc, email, "123456")).post("/api/v1/registrations");
    }

    private Response login(String type, String doc, String pwd) {
        return given().contentType(ContentType.JSON)
                .body("{\"documentType\":\"" + type + "\",\"documentNumber\":\"" + doc + "\",\"password\":\"" + pwd + "\"}")
                .post("/api/v1/auth/login");
    }

    @Test
    void invalidFieldsAreRejectedWith422AndNothingPersisted() {
        String doc = newDoc();
        for (String bad : List.of("1234567", "ABC12345", "7375 7451", "7375745-", " 7375745", "123456789")) {
            given().contentType(ContentType.JSON).body(body("DNI", bad, "a@example.com", "123456"))
                    .post("/api/v1/registrations").then().statusCode(422)
                    .body("code", equalTo("VALIDATION_ERROR"))
                    .body("details.errors.field", hasItem("documentNumber"));
        }
        given().contentType(ContentType.JSON).body(body("LE", doc, "usuario@", "12345"))
                .post("/api/v1/registrations").then().statusCode(422)
                .body("details.errors.field", hasItem("documentType"))
                .body("details.errors.field", hasItem("email"))
                .body("details.errors.field", hasItem("password"));
        // número JSON en lugar de string
        given().contentType(ContentType.JSON)
                .body("{\"documentType\":\"DNI\",\"documentNumber\":12345678,\"email\":\"a@example.com\",\"password\":\"123456\"}")
                .post("/api/v1/registrations").then().statusCode(400).body("code", equalTo("INVALID_REQUEST"));
        login("DNI", doc, "123456").then().statusCode(401);
    }

    @Test
    void fullFlowActivatesWalletAndSupportsLoginAndLogout() {
        String doc = "00" + newDoc().substring(2);
        String email = "u" + doc + "@example.com";
        Response reg = register(doc, email);
        reg.then().statusCode(201).body("customerStatus", equalTo("UNVALIDATED")).body("currentStep", equalTo(2))
                .body("totalSteps", equalTo(2));
        String id = reg.path("registrationId");
        String otp = mailbox.lastCode(email).orElseThrow();

        // no validado: no puede iniciar sesión (credenciales correctas -> 403)
        login("DNI", doc, "123456").then().statusCode(403).body("code", equalTo("CUSTOMER_NOT_VALIDATED"));

        String wrong = otp.equals("000000") ? "111111" : "000000";
        given().contentType(ContentType.JSON).body("{\"otp\":\"" + wrong + "\"}")
                .post("/api/v1/registrations/" + id + "/otp/verify").then().statusCode(422)
                .body("code", equalTo("OTP_INVALID"));
        given().get("/api/v1/registrations/" + id).then().statusCode(200).body("customerStatus", equalTo("UNVALIDATED"));

        given().contentType(ContentType.JSON).body("{\"otp\":\"" + otp + "\"}")
                .post("/api/v1/registrations/" + id + "/otp/verify").then().statusCode(200)
                .body("customerStatus", equalTo("VALIDATED")).body("wallet.status", equalTo("ACTIVE"))
                .body("onboardingCompleted", equalTo(true));
        // idempotente
        given().contentType(ContentType.JSON).body("{\"otp\":\"" + otp + "\"}")
                .post("/api/v1/registrations/" + id + "/otp/verify").then().statusCode(200);

        List<AnalyticsEvent> evs = events.findBySession(UUID.fromString(id));
        assertEquals(4, evs.size());
        assertTrue(evs.stream().anyMatch(e -> e.eventType().name().equals("ONBOARDING_CLOSED")));
        assertFalse(evs.toString().contains("123456"));

        login("DNI", doc, "654321").then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        login("CE", doc, "123456").then().statusCode(401);

        String token = login("DNI", doc, "123456").then().statusCode(200).body("tokenType", equalTo("Bearer"))
                .body("walletStatus", equalTo("ACTIVE")).extract().path("accessToken");
        given().header("Authorization", "Bearer " + token).get("/api/v1/wallets/me").then().statusCode(200)
                .body("status", equalTo("ACTIVE"));

        given().header("Authorization", "Bearer " + token).post("/api/v1/auth/logout").then().statusCode(204);
        given().header("Authorization", "Bearer " + token).post("/api/v1/auth/logout").then().statusCode(204);
        given().header("Authorization", "Bearer " + token).get("/api/v1/wallets/me").then().statusCode(401);
        given().post("/api/v1/auth/logout").then().statusCode(401);

        String again = login("DNI", doc, "123456").then().statusCode(200).extract().path("accessToken");
        given().header("Authorization", "Bearer " + again).get("/api/v1/wallets/me").then().statusCode(200);
        register(doc, email).then().statusCode(409).body("code", equalTo("DOCUMENT_ALREADY_REGISTERED"));
    }

    @Test
    void resendInvalidatesPreviousOtp() {
        String doc = newDoc();
        String email = "r" + doc + "@example.com";
        String id = register(doc, email).path("registrationId");
        String first = mailbox.lastCode(email).orElseThrow();
        given().post("/api/v1/registrations/" + id + "/otp/resend").then().statusCode(202);
        String second = mailbox.lastCode(email).orElseThrow();
        if (!first.equals(second)) {
            given().contentType(ContentType.JSON).body("{\"otp\":\"" + first + "\"}")
                    .post("/api/v1/registrations/" + id + "/otp/verify").then().statusCode(422);
        }
        given().contentType(ContentType.JSON).body("{\"otp\":\"" + second + "\"}")
                .post("/api/v1/registrations/" + id + "/otp/verify").then().statusCode(200);
        given().post("/api/v1/registrations/" + id + "/otp/resend").then().statusCode(409);
        given().get("/api/v1/registrations/not-a-uuid").then().statusCode(404)
                .body("code", equalTo("REGISTRATION_NOT_FOUND"));
    }

    @Test
    void messagesDoNotMentionTestEnvironment() {
        String text = given().contentType(ContentType.JSON).body(body("XX", "abc", "x", "1"))
                .post("/api/v1/registrations").then().statusCode(422).extract().asString().toLowerCase();
        assertFalse(text.contains("test"));
        given().contentType(ContentType.JSON).body("{\"documentType\":\"DNI\",\"documentNumber\":\"12345678\",\"password\":\"123456\"}")
                .post("/api/v1/auth/login").then().statusCode(401).body(not(containsString("test")));
    }

    @Test
    void loginIsLockedAfterRepeatedFailures() {
        String doc = newDoc();
        for (int i = 0; i < 3; i++) {
            login("DNI", doc, "111111").then().statusCode(401);
        }
        login("DNI", doc, "111111").then().statusCode(429).body("code", equalTo("LOGIN_RATE_LIMITED"));
    }
}
