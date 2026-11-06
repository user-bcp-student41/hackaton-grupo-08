package com.ailabs.registration;

import java.time.Instant;
import java.util.UUID;

/** Cliente. {@code documentNumber} es texto de 8 caracteres (conserva ceros iniciales). */
public record Customer(
        UUID id,
        String documentType,
        String documentNumber,
        String email,
        CustomerStatus status,
        String passwordHash,
        Instant createdAt) {

    public Customer validated() {
        return new Customer(id, documentType, documentNumber, email, CustomerStatus.VALIDATED, passwordHash,
                createdAt);
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", status=" + status + "]";
    }
}
