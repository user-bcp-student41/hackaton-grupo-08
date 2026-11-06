package com.ailabs.payments.api;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.api.UserIdResolver;
import com.ailabs.payments.domain.PaymentStatus;
import com.ailabs.payments.service.PaymentCommand;
import com.ailabs.payments.service.PaymentPage;
import com.ailabs.payments.service.PaymentResult;
import com.ailabs.payments.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * B1: resuelve identidad, valida la forma de la petición (tipos, obligatoriedad, formato de
 * {@code Idempotency-Key}), delega en {@link PaymentService} y serializa. Sin reglas de negocio.
 */
@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;
    private static final Pattern UUID_FORMAT = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Inject
    PaymentService service;

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    public ValidationResponse validate(@HeaderParam(UserIdResolver.HEADER) String userHeader, PaymentRequest body) {
        String userId = UserIdResolver.resolve(userHeader);
        PaymentCommand command = PaymentRequest.toCommand(body);
        return ValidationResponse.from(service.validate(userId, command));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @HeaderParam(IDEMPOTENCY_HEADER) String idempotencyKey, PaymentRequest body) {
        String userId = UserIdResolver.resolve(userHeader);
        String key = parseKey(idempotencyKey);
        PaymentCommand command = PaymentRequest.toCommand(body);
        return toResponse(service.create(userId, key, command));
    }

    @GET
    @Path("/{paymentId}")
    public PaymentResponse get(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @PathParam("paymentId") String paymentId) {
        String userId = UserIdResolver.resolve(userHeader);
        return PaymentResponse.from(service.get(userId, paymentId));
    }

    @POST
    @Path("/{paymentId}/retry")
    public Response retry(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @HeaderParam(IDEMPOTENCY_HEADER) String idempotencyKey, @PathParam("paymentId") String paymentId) {
        String userId = UserIdResolver.resolve(userHeader);
        String key = parseKey(idempotencyKey);
        return toResponse(service.retry(userId, paymentId, key));
    }

    @GET
    public HistoryResponse history(@HeaderParam(UserIdResolver.HEADER) String userHeader,
            @QueryParam("page") String page, @QueryParam("size") String size, @QueryParam("status") String status) {
        String userId = UserIdResolver.resolve(userHeader);
        int pageNumber = parseInt(page, 0, 0, Integer.MAX_VALUE / MAX_PAGE_SIZE, "page");
        int pageSize = parseInt(size, DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE, "size");
        PaymentStatus filter = parseStatus(status);
        PaymentPage result = service.history(userId, filter, pageNumber, pageSize);
        return HistoryResponse.from(result);
    }

    private static Response toResponse(PaymentResult result) {
        PaymentResponse entity = PaymentResponse.from(result.payment());
        Response.ResponseBuilder builder = Response.status(result.created() ? 202 : 200).entity(entity);
        if (result.created()) {
            builder.location(URI.create("/api/v1/payments/" + entity.paymentId()));
        }
        return builder.build();
    }

    private static String parseKey(String header) {
        if (header == null || !UUID_FORMAT.matcher(header.trim()).matches()) {
            throw ApiException.invalidFields(List.of(Map.of("field", IDEMPOTENCY_HEADER, "reason", "MUST_BE_UUID")));
        }
        return header.trim().toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String raw, int defaultValue, int min, int max, String field) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw ApiException.invalidFields(List.of(Map.of("field", field, "reason", "OUT_OF_RANGE")));
        }
    }

    private static PaymentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException invalid) {
            throw ApiException.invalidFields(List.of(Map.of("field", "status", "reason", "INVALID_VALUE")));
        }
    }
}
