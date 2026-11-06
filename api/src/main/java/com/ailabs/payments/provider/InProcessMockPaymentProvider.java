package com.ailabs.payments.provider;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock EN PROCESO del proveedor, solo para la demo y las pruebas (no es el mock HTTP de
 * {@code docs/bcp-wallet-mock-openapi.yaml}; ver pendientes en developer_spec). El escenario se elige de forma
 * determinista con valores reservados de {@code recipientRef}:
 *
 * <ul>
 *   <li>{@code mock-success} (ES-1, y comportamiento por defecto): PENDING en la 1.ª consulta, SUCCEEDED después.
 *   <li>{@code mock-instant}: el envío ya devuelve SUCCEEDED.
 *   <li>{@code mock-rejected} (ES-2): el envío devuelve REJECTED.
 *   <li>{@code mock-fail-async}: PENDING en la 1.ª consulta, FAILED después.
 *   <li>{@code mock-never-resolves} (ES-3): nunca informa estado; el intento expira.
 *   <li>{@code mock-timeout} (ES-4): el envío devuelve UNKNOWN; nunca informa estado.
 *   <li>{@code mock-not-found} (ES-5): el destinatario no existe.
 *   <li>{@code mock-unavailable}: el proveedor no responde al resolver el destinatario (503).
 *   <li>{@code mock-late-success} (ES-7): PENDING en la 1.ª consulta y SUCCEEDED en la 2.ª (confirmación tardía si
 *       el intento ya expiró).
 * </ul>
 *
 * Todo importe/destinatario es sintético. Contadores en memoria (NO durables).
 */
@ApplicationScoped
public class InProcessMockPaymentProvider implements PaymentProvider {

    public static final String SUCCESS = "mock-success";
    public static final String INSTANT = "mock-instant";
    public static final String REJECTED = "mock-rejected";
    public static final String FAIL_ASYNC = "mock-fail-async";
    public static final String NEVER_RESOLVES = "mock-never-resolves";
    public static final String TIMEOUT = "mock-timeout";
    public static final String NOT_FOUND = "mock-not-found";
    public static final String UNAVAILABLE = "mock-unavailable";
    public static final String LATE_SUCCESS = "mock-late-success";

    private final Map<UUID, String> scenarioByPayment = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> queries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> submits = new ConcurrentHashMap<>();

    @Override
    public Optional<RecipientInfo> resolveRecipient(String recipientRef, String providerId, String channel) {
        if (NOT_FOUND.equals(recipientRef)) {
            return Optional.empty();
        }
        if (UNAVAILABLE.equals(recipientRef)) {
            throw new ProviderUnavailableException("Proveedor simulado no disponible");
        }
        return Optional.of(new RecipientInfo("Contacto de prueba", mask(recipientRef)));
    }

    @Override
    public SubmitOutcome submit(SubmitRequest request) {
        submits.merge(request.paymentId(), 1, Integer::sum);
        scenarioByPayment.put(request.paymentId(), request.recipientRef());
        return switch (request.recipientRef()) {
            case INSTANT -> SubmitOutcome.succeeded(reference(request.paymentId()));
            case REJECTED -> SubmitOutcome.rejected("PROVIDER_REJECTED");
            case TIMEOUT -> SubmitOutcome.unknown();
            default -> SubmitOutcome.acceptedPending();
        };
    }

    @Override
    public ProviderStatus queryStatus(UUID paymentId, String providerId) {
        String scenario = scenarioByPayment.get(paymentId);
        if (scenario == null) {
            return ProviderStatus.notSupported();
        }
        int call = queries.computeIfAbsent(paymentId, id -> new AtomicInteger()).incrementAndGet();
        return switch (scenario) {
            case NEVER_RESOLVES, TIMEOUT -> ProviderStatus.notSupported();
            case INSTANT -> ProviderStatus.succeeded(reference(paymentId));
            case REJECTED -> ProviderStatus.failed("PROVIDER_REJECTED");
            case FAIL_ASYNC -> call < 2 ? ProviderStatus.pending() : ProviderStatus.failed("PROVIDER_REJECTED");
            default -> call < 2 ? ProviderStatus.pending() : ProviderStatus.succeeded(reference(paymentId));
        };
    }

    /** Cantidad de envíos recibidos para un pago (verifica que no hay cobros duplicados en pruebas). */
    public int submitCount(UUID paymentId) {
        return submits.getOrDefault(paymentId, 0);
    }

    private static String reference(UUID paymentId) {
        return "REF-" + paymentId.toString().substring(0, 8).toUpperCase();
    }

    static String mask(String recipientRef) {
        String tail = recipientRef.length() <= 3 ? recipientRef : recipientRef.substring(recipientRef.length() - 3);
        return "***" + tail;
    }
}
