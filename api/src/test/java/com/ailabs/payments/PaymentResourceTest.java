package com.ailabs.payments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailabs.payments.domain.CheckoutEvent;
import com.ailabs.payments.domain.CheckoutEventType;
import com.ailabs.payments.persistence.CheckoutEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PaymentResourceTest {

    private static final String PAYMENTS = "/api/v1/payments";
    private static final String SESSIONS = "/api/v1/onboarding/sessions";

    @Inject
    CheckoutEventRepository events;

    /** Crea un usuario con wallet ACTIVE completando el onboarding por la API (HU-01). */
    private static String activeUser() {
        String user = "pay-" + UUID.randomUUID().toString().substring(0, 8);
        String sessionId = given().header("X-User-Id", user).contentType(ContentType.JSON).body("{}")
                .when().post(SESSIONS).then().statusCode(201).extract().path("sessionId");
        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"data\":{\"documentType\":\"TEST\",\"document\":\"TEST-001\"}}")
                .when().put(SESSIONS + "/" + sessionId + "/steps/DOCUMENT").then().statusCode(200);
        given().header("X-User-Id", user).contentType(ContentType.JSON).body("{\"data\":{}}")
                .when().put(SESSIONS + "/" + sessionId + "/steps/VERIFICATION").then().statusCode(200);
        given().header("X-User-Id", user).when().post(SESSIONS + "/" + sessionId + "/complete").then()
                .statusCode(200);
        return user;
    }

    private static String body(String recipientRef, String amount) {
        return "{\"recipientRef\":\"" + recipientRef + "\",\"amount\":" + amount
                + ",\"currency\":\"PEN\",\"providerId\":\"mock-provider-a\",\"channel\":\"ALIAS\"}";
    }

    private static ValidatableResponse post(String user, String key, String json) {
        var request = given().header("X-User-Id", user).contentType(ContentType.JSON).body(json);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.when().post(PAYMENTS).then();
    }

    private static ValidatableResponse get(String user, String id) {
        return given().header("X-User-Id", user).when().get(PAYMENTS + "/" + id).then();
    }

    @Test
    void validateResolvesDemoContactsByTheirFixedName() {
        String user = activeUser();
        given().header("X-User-Id", user).contentType(ContentType.JSON).body(body("77777777", "20.00"))
                .when().post(PAYMENTS + "/validate").then()
                .statusCode(200)
                .body("recipient.displayName", equalTo("Valeria Quispe"))
                .body("recipient.maskedAlias", equalTo("***777"));
    }

    @Test
    void validateShowsRecipientAndAmountWithoutCreatingAnyPayment() {
        String user = activeUser();
        given().header("X-User-Id", user).contentType(ContentType.JSON).body(body("contacto-123", "50.00"))
                .when().post(PAYMENTS + "/validate").then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("recipient.displayName", equalTo("Contacto de prueba"))
                .body("recipient.maskedAlias", equalTo("***123"))
                .body("amount", equalTo(50.0f))
                .body("currency", equalTo("PEN"));
        given().header("X-User-Id", user).when().get(PAYMENTS).then().statusCode(200)
                .body("items", hasSize(0)).body("totalItems", equalTo(0));
    }

    @Test
    void successfulPaymentGoesPendingThenSuccessWithReferenceAndAppearsOnceInHistory() {
        String user = activeUser();
        String key = UUID.randomUUID().toString();
        String id = post(user, key, body("mock-success", "50.00"))
                .statusCode(202)
                .header("Location", containsString(PAYMENTS + "/"))
                .body("status", equalTo("PENDING"))
                .body("terminal", equalTo(false))
                .body("retryable", equalTo(false))
                .body("transactionReference", nullValue())
                .body("recipient.maskedAlias", equalTo("***ess"))
                .extract().path("paymentId");

        get(user, id).statusCode(200).body("status", equalTo("PENDING")).body("terminal", equalTo(false));
        get(user, id).statusCode(200).body("status", equalTo("SUCCESS")).body("terminal", equalTo(true))
                .body("transactionReference", notNullValue()).body("terminalAt", notNullValue());

        post(user, key, body("mock-success", "50.00")).statusCode(200).body("paymentId", equalTo(id));

        String reference = get(user, id).extract().path("transactionReference");
        given().header("X-User-Id", user).when().get(PAYMENTS).then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].paymentId", equalTo(id))
                .body("items[0].status", equalTo("SUCCESS"))
                .body("items[0].transactionReference", equalTo(reference));

        long activations = events.findAll().stream()
                .filter(e -> e.eventType() == CheckoutEventType.PAYER_ACTIVATED && id.equals(String.valueOf(e.paymentId())))
                .count();
        assertEquals(1, activations);
    }

    @Test
    void failedPaymentOffersOneRetryAndNeverDuplicatesTheCharge() {
        String user = activeUser();
        String id = post(user, UUID.randomUUID().toString(), body("mock-rejected", "10.00"))
                .statusCode(202).body("status", equalTo("FAILED")).body("retryable", equalTo(true))
                .body("failureCode", equalTo("PROVIDER_REJECTED")).extract().path("paymentId");

        String retryKey = UUID.randomUUID().toString();
        String retryId = given().header("X-User-Id", user).header("Idempotency-Key", retryKey)
                .when().post(PAYMENTS + "/" + id + "/retry").then()
                .statusCode(202).body("retryOf", equalTo(id)).extract().path("paymentId");
        given().header("X-User-Id", user).header("Idempotency-Key", retryKey)
                .when().post(PAYMENTS + "/" + id + "/retry").then()
                .statusCode(200).body("paymentId", equalTo(retryId));
        given().header("X-User-Id", user).header("Idempotency-Key", UUID.randomUUID().toString())
                .when().post(PAYMENTS + "/" + id + "/retry").then()
                .statusCode(409).body("code", equalTo("PAYMENT_NOT_RETRYABLE"))
                .body("details.reason", equalTo("ALREADY_RETRIED"));
        get(user, id).body("status", equalTo("FAILED")).body("retryable", equalTo(false));
    }

    @Test
    void retryOfPendingPaymentIsConflict() {
        String user = activeUser();
        String id = post(user, UUID.randomUUID().toString(), body("mock-never-resolves", "10.00"))
                .statusCode(202).extract().path("paymentId");
        given().header("X-User-Id", user).header("Idempotency-Key", UUID.randomUUID().toString())
                .when().post(PAYMENTS + "/" + id + "/retry").then()
                .statusCode(409).body("details.reason", equalTo("PAYMENT_PENDING"));
    }

    @Test
    void amountOverTheLimitIsRejectedWithoutValueAndAmountEqualToLimitIsAccepted() {
        String user = activeUser();
        post(user, UUID.randomUUID().toString(), body("mock-instant", "500.01"))
                .statusCode(422).body("code", equalTo("AMOUNT_LIMIT_EXCEEDED"))
                .body("details.field", equalTo("amount")).body("message", not(containsString("500")));
        post(user, UUID.randomUUID().toString(), body("mock-instant", "500.00")).statusCode(202)
                .body("status", equalTo("SUCCESS"));
        given().header("X-User-Id", user).when().get(PAYMENTS).then().body("items", hasSize(1));
        assertTrue(events.findAll().stream().map(CheckoutEvent::eventType)
                .anyMatch(t -> t == CheckoutEventType.CHECKOUT_LIMIT_REJECTED));
    }

    @Test
    void invalidAmountsAndShapesAreBadRequestsWithTheFieldAndNoPayment() {
        String user = activeUser();
        String key = UUID.randomUUID().toString();
        for (String amount : new String[] {"0", "-5", "10.123", "\"abc\"", "null"}) {
            post(user, key, body("mock-instant", amount)).statusCode(400).body("code", equalTo("INVALID_REQUEST"))
                    .body("details.errors[0].field", equalTo("amount"));
        }
        post(user, null, body("mock-instant", "10.00")).statusCode(400)
                .body("details.errors[0].field", equalTo("Idempotency-Key"));
        post(user, "no-uuid", body("mock-instant", "10.00")).statusCode(400);
        post(user, key, "{\"recipientRef\":\"x\",\"amount\":1,\"currency\":\"USD\",\"providerId\":\"mock-provider-a\","
                + "\"channel\":\"ALIAS\"}").statusCode(400).body("details.errors[0].field", equalTo("currency"));
        post(user, key, body("mock-instant", "10.00").replace("}", ",\"collectionId\":\"c1\"}")).statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
        given().header("X-User-Id", user).when().get(PAYMENTS).then().body("items", hasSize(0));
    }

    @Test
    void sameKeyWithDifferentBodyIsConflict() {
        String user = activeUser();
        String key = UUID.randomUUID().toString();
        post(user, key, body("mock-success", "20.00")).statusCode(202);
        post(user, key, body("mock-success", "21.00")).statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void unsupportedChannelRecipientNotFoundAndInactiveWalletAreReported() {
        String user = activeUser();
        post(user, UUID.randomUUID().toString(), body("mock-instant", "10.00").replace("ALIAS", "OTRO"))
                .statusCode(422).body("code", equalTo("PROVIDER_CHANNEL_UNSUPPORTED"));
        post(user, UUID.randomUUID().toString(), body("mock-not-found", "10.00")).statusCode(404)
                .body("code", equalTo("RECIPIENT_NOT_FOUND"));
        post(user, UUID.randomUUID().toString(), body("mock-unavailable", "10.00")).statusCode(503)
                .body("code", equalTo("PROVIDER_UNAVAILABLE"));

        String noWallet = "pay-nowallet-" + UUID.randomUUID().toString().substring(0, 6);
        post(noWallet, UUID.randomUUID().toString(), body("mock-instant", "10.00")).statusCode(409)
                .body("code", equalTo("WALLET_NOT_ACTIVE"));
    }

    @Test
    void identityIsRequiredAndOtherUsersCannotReadOrRetryAPayment() {
        String owner = activeUser();
        String other = activeUser();
        String id = post(owner, UUID.randomUUID().toString(), body("mock-rejected", "10.00"))
                .statusCode(202).extract().path("paymentId");

        given().when().get(PAYMENTS).then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        given().when().get(PAYMENTS + "/" + id).then().statusCode(401);
        get(other, id).statusCode(404).body("code", equalTo("PAYMENT_NOT_FOUND"));
        get(owner, "no-es-uuid").statusCode(404).body("code", equalTo("PAYMENT_NOT_FOUND"));
        given().header("X-User-Id", other).header("Idempotency-Key", UUID.randomUUID().toString())
                .when().post(PAYMENTS + "/" + id + "/retry").then().statusCode(404);
        given().header("X-User-Id", other).when().get(PAYMENTS).then().body("items", hasSize(0));
    }

    @Test
    void historyParametersAreValidated() {
        String user = activeUser();
        given().header("X-User-Id", user).queryParam("size", 101).when().get(PAYMENTS).then().statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
        given().header("X-User-Id", user).queryParam("page", -1).when().get(PAYMENTS).then().statusCode(400);
        given().header("X-User-Id", user).queryParam("status", "NOPE").when().get(PAYMENTS).then().statusCode(400);
        given().header("X-User-Id", user).queryParam("status", "SUCCESS").when().get(PAYMENTS).then().statusCode(200);
    }

    @Test
    void checkoutEventsAcceptOnlyUiMilestonesOfOwnPayments() {
        String user = activeUser();
        String id = post(user, UUID.randomUUID().toString(), body("mock-never-resolves", "10.00"))
                .statusCode(202).extract().path("paymentId");
        String url = "/api/v1/checkout-events";

        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"eventType\":\"CHECKOUT_CONFIRMATION_VIEWED\"}").when().post(url).then().statusCode(202);
        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"eventType\":\"CHECKOUT_PENDING_SHOWN\",\"paymentId\":\"" + id + "\"}")
                .when().post(url).then().statusCode(202);
        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"eventType\":\"PAYER_ACTIVATED\"}").when().post(url).then().statusCode(400);
        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"eventType\":\"CHECKOUT_PENDING_SHOWN\"}").when().post(url).then().statusCode(400);
        given().header("X-User-Id", activeUser()).contentType(ContentType.JSON)
                .body("{\"eventType\":\"CHECKOUT_PENDING_SHOWN\",\"paymentId\":\"" + id + "\"}")
                .when().post(url).then().statusCode(404);
        given().contentType(ContentType.JSON).body("{\"eventType\":\"CHECKOUT_CONFIRMATION_VIEWED\"}")
                .when().post(url).then().statusCode(401);
    }
}
