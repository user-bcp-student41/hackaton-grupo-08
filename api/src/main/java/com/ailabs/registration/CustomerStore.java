package com.ailabs.registration;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Persistencia de clientes, registros, OTP y sesiones de login. {@link #withLock} da atomicidad. */
public interface CustomerStore {

    <T> T withLock(Supplier<T> action);

    Optional<Customer> findCustomer(UUID id);

    Optional<Customer> findByDocument(String documentType, String documentNumber);

    void saveCustomer(Customer customer);

    Optional<Registration> findRegistration(UUID id);

    void saveRegistration(Registration registration);

    void deleteRegistrationsOf(UUID customerId);

    Optional<OtpRecord> findOtp(UUID customerId);

    void saveOtp(UUID customerId, OtpRecord otp);

    void deleteOtp(UUID customerId);

    void saveSession(LoginSession session);

    Optional<LoginSession> findSessionByTokenHash(String tokenHash);
}
