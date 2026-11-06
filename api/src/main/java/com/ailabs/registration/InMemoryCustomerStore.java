package com.ailabs.registration;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Almacén en memoria (NO durable), como el onboarding actual (decisión D1; pendiente P7). */
@ApplicationScoped
public class InMemoryCustomerStore implements CustomerStore {

    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, UUID> byDocument = new ConcurrentHashMap<>();
    private final Map<UUID, Registration> registrations = new ConcurrentHashMap<>();
    private final Map<UUID, OtpRecord> otps = new ConcurrentHashMap<>();
    private final Map<String, LoginSession> sessionsByHash = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Customer> findCustomer(UUID id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public Optional<Customer> findByDocument(String documentType, String documentNumber) {
        UUID id = byDocument.get(documentType + "|" + documentNumber);
        return id == null ? Optional.empty() : findCustomer(id);
    }

    @Override
    public void saveCustomer(Customer customer) {
        customers.put(customer.id(), customer);
        byDocument.put(customer.documentType() + "|" + customer.documentNumber(), customer.id());
    }

    @Override
    public Optional<Registration> findRegistration(UUID id) {
        return Optional.ofNullable(registrations.get(id));
    }

    @Override
    public void saveRegistration(Registration registration) {
        registrations.put(registration.id(), registration);
    }

    @Override
    public void deleteRegistrationsOf(UUID customerId) {
        registrations.values().removeIf(r -> r.customerId().equals(customerId));
    }

    @Override
    public Optional<OtpRecord> findOtp(UUID customerId) {
        return Optional.ofNullable(otps.get(customerId));
    }

    @Override
    public void saveOtp(UUID customerId, OtpRecord otp) {
        otps.put(customerId, otp);
    }

    @Override
    public void deleteOtp(UUID customerId) {
        otps.remove(customerId);
    }

    @Override
    public void saveSession(LoginSession session) {
        sessionsByHash.put(session.tokenHash(), session);
    }

    @Override
    public Optional<LoginSession> findSessionByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessionsByHash.get(tokenHash));
    }
}
