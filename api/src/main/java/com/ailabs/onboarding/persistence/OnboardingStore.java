package com.ailabs.onboarding.persistence;

import com.ailabs.onboarding.domain.OnboardingSession;
import com.ailabs.onboarding.domain.SessionStatus;
import com.ailabs.onboarding.domain.Wallet;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Almacén en memoria (NO durable) de sesiones y wallets. {@link #withLock} emula la sección crítica de una
 * transacción: los servicios agregan el evento primero y guardan el estado después, de modo que un fallo al
 * registrar el evento no deja estado modificado (CA-16).
 */
@ApplicationScoped
public class OnboardingStore {

    private final Map<UUID, OnboardingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Wallet> walletsByUser = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public Optional<OnboardingSession> findSession(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Optional<OnboardingSession> findInProgressByUser(String userId) {
        return sessions.values().stream()
                .filter(session -> session.userId().equals(userId) && session.status() == SessionStatus.IN_PROGRESS)
                .findFirst();
    }

    public void saveSession(OnboardingSession session) {
        sessions.put(session.id(), session);
    }

    public Optional<Wallet> findWalletByUser(String userId) {
        return Optional.ofNullable(walletsByUser.get(userId));
    }

    public void saveWallet(Wallet wallet) {
        walletsByUser.put(wallet.userId(), wallet);
    }
}
