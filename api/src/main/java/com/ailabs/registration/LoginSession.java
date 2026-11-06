package com.ailabs.registration;

import java.time.Instant;
import java.util.UUID;

/** Sesión de login: solo se guarda el hash del token. */
public record LoginSession(UUID id, UUID customerId, String tokenHash, Instant createdAt, Instant expiresAt,
        Instant revokedAt) {

    public boolean activeAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public LoginSession revoke(Instant at) {
        return new LoginSession(id, customerId, tokenHash, createdAt, expiresAt, at);
    }

    @Override
    public String toString() {
        return "LoginSession[id=" + id + "]";
    }
}
