package com.ailabs.payments.persistence;

import com.ailabs.payments.domain.PaymentAttempt;
import com.ailabs.payments.domain.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementación en memoria (NO durable) de {@link PaymentStore}; pendiente P3 (durabilidad con Datos/PO). */
@ApplicationScoped
public class InMemoryPaymentStore implements PaymentStore {

    private final Map<UUID, PaymentAttempt> attempts = new HashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
    private final Map<UUID, UUID> retryChildByOriginal = new HashMap<>();

    private static String idKey(String userId, String key) {
        return userId + "|" + key;
    }

    @Override
    public synchronized Optional<IdempotencyRecord> findIdempotency(String userId, String key) {
        return Optional.ofNullable(idempotency.get(idKey(userId, key)));
    }

    @Override
    public synchronized CreateResult createIdempotent(PaymentAttempt attempt, String key, String fingerprint) {
        IdempotencyRecord existing = idempotency.get(idKey(attempt.userId(), key));
        if (existing != null) {
            return new CreateResult(CreateKind.EXISTING, attempts.get(existing.paymentId()), existing.fingerprint());
        }
        if (attempt.retryOf() != null && retryChildByOriginal.containsKey(attempt.retryOf())) {
            return new CreateResult(CreateKind.RETRY_ALREADY_EXISTS, null, null);
        }
        attempts.put(attempt.id(), attempt);
        idempotency.put(idKey(attempt.userId(), key),
                new IdempotencyRecord(attempt.userId(), key, fingerprint, attempt.id()));
        if (attempt.retryOf() != null) {
            retryChildByOriginal.put(attempt.retryOf(), attempt.id());
        }
        return new CreateResult(CreateKind.CREATED, attempt, fingerprint);
    }

    @Override
    public synchronized Optional<PaymentAttempt> findOwned(String userId, UUID paymentId) {
        return Optional.ofNullable(attempts.get(paymentId)).filter(a -> a.userId().equals(userId));
    }

    @Override
    public synchronized boolean transition(PaymentAttempt expected, PaymentAttempt updated) {
        PaymentAttempt current = attempts.get(expected.id());
        if (current == null || current.status() != PaymentStatus.PENDING || current.version() != expected.version()) {
            return false;
        }
        attempts.put(updated.id(), updated);
        return true;
    }

    @Override
    public synchronized Optional<PaymentAttempt> findRetryChild(UUID originalId) {
        return Optional.ofNullable(retryChildByOriginal.get(originalId)).map(attempts::get);
    }

    @Override
    public synchronized List<PaymentAttempt> findPendingByUser(String userId) {
        return attempts.values().stream()
                .filter(a -> a.userId().equals(userId) && a.status() == PaymentStatus.PENDING)
                .toList();
    }

    @Override
    public synchronized Page history(String userId, PaymentStatus status, int page, int size) {
        List<PaymentAttempt> all = new ArrayList<>(attempts.values().stream()
                .filter(a -> a.userId().equals(userId) && (status == null || a.status() == status))
                .toList());
        all.sort(Comparator.comparing(PaymentAttempt::createdAt).reversed().thenComparing(PaymentAttempt::id));
        long from = (long) page * size;
        List<PaymentAttempt> items = from >= all.size()
                ? List.of()
                : all.subList((int) from, (int) Math.min(all.size(), from + size));
        return new Page(List.copyOf(items), page, size, all.size());
    }
}
