package com.ailabs.payments.persistence;

import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacén de intentos de pago y registros de idempotencia. Las operaciones críticas son atómicas:
 * crear-si-no-existe por (userId, clave) y transición condicionada a {@code PENDING} con control de versión.
 */
public interface PaymentStore {

    enum CreateKind {
        CREATED,
        /** Ya existía un registro para (userId, clave): se devuelve el intento original (replay). */
        EXISTING,
        /** El intento original ya tiene un reintento hijo con otra clave. */
        RETRY_ALREADY_EXISTS
    }

    record CreateResult(CreateKind kind, PaymentAttempt attempt, String fingerprint) {
    }

    record IdempotencyRecord(String userId, String key, String fingerprint, UUID paymentId) {
    }

    record Page(List<PaymentAttempt> items, int page, int size, long totalItems) {
    }

    Optional<IdempotencyRecord> findIdempotency(String userId, String key);

    /**
     * Guarda el intento y su registro de idempotencia en una sola operación atómica. Si {@code attempt.retryOf()}
     * no es nulo, además garantiza un único hijo por original.
     */
    CreateResult createIdempotent(PaymentAttempt attempt, String key, String fingerprint);

    /** Intento SOLO si pertenece al usuario; otro usuario o inexistente es indistinguible. */
    Optional<PaymentAttempt> findOwned(String userId, UUID paymentId);

    /** Reemplaza {@code expected} por {@code updated} solo si en el almacén sigue siendo {@code PENDING} y con la misma versión. */
    boolean transition(PaymentAttempt expected, PaymentAttempt updated);

    Optional<PaymentAttempt> findRetryChild(UUID originalId);

    List<PaymentAttempt> findPendingByUser(String userId);

    /** Historial del usuario, orden {@code createdAt} descendente; {@code status} opcional. */
    Page history(String userId, PaymentStatus status, int page, int size);
}
