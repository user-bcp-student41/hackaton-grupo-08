package com.ailabs.registration;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.domain.WalletStatus;
import com.ailabs.onboarding.persistence.OnboardingStore;
import com.ailabs.registration.RegistrationResponses.LoginView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Login, sesión opaca revocable (solo se guarda el hash del token) y bloqueo por intentos fallidos. */
@ApplicationScoped
public class AuthService {

    private record Failures(int count, Instant lockedUntil) {
    }

    @Inject
    CustomerStore store;
    @Inject
    OnboardingStore onboardingStore;
    @Inject
    CredentialHasher hasher;
    @Inject
    Clock clock;

    @ConfigProperty(name = "auth.session-ttl-seconds")
    long sessionTtlSeconds;
    @ConfigProperty(name = "auth.max-failed-logins")
    int maxFailed;
    @ConfigProperty(name = "auth.lockout-seconds")
    long lockoutSeconds;
    @ConfigProperty(name = "onboarding.payments-entry-point", defaultValue = "/payments/new")
    String paymentsEntryPoint;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginView login(LoginRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Envía tus credenciales.");
        }
        List<FieldIssue> issues = new ArrayList<>();
        FieldRules.documentType(req.documentType(), issues);
        FieldRules.documentNumber(req.documentNumber(), issues);
        FieldRules.password(req.password(), issues);
        if (!issues.isEmpty()) {
            throw ApiException.validation("LOGIN", issues);
        }
        Instant now = clock.instant();
        String key = req.documentType() + "|" + req.documentNumber();
        Failures f = failures.get(key);
        if (f != null && f.lockedUntil() != null && now.isBefore(f.lockedUntil())) {
            throw RegistrationErrors.loginRateLimited();
        }
        Optional<Customer> found = store.findByDocument(req.documentType(), req.documentNumber());
        boolean ok = hasher.matches(req.password(), found.map(Customer::passwordHash).orElse(hasher.dummyHash()))
                && found.isPresent();
        if (!ok) {
            registerFailure(key, now);
            throw RegistrationErrors.invalidCredentials();
        }
        failures.remove(key);
        Customer customer = found.get();
        if (customer.status() != CustomerStatus.VALIDATED) {
            throw RegistrationErrors.customerNotValidated();
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = now.plusSeconds(sessionTtlSeconds);
        store.saveSession(new LoginSession(UUID.randomUUID(), customer.id(), CredentialHasher.sha256(token), now,
                expires, null));
        String walletStatus = onboardingStore.findWalletByUser(customer.id().toString())
                .map(w -> w.status().name()).orElse(WalletStatus.PENDING.name());
        return new LoginView(token, "Bearer", expires, walletStatus, paymentsEntryPoint);
    }

    /** Idempotente: una sesión desconocida, expirada o ya revocada no produce error. */
    public void logout(String token) {
        Instant now = clock.instant();
        store.withLock(() -> {
            store.findSessionByTokenHash(CredentialHasher.sha256(token)).ifPresent(s -> {
                if (s.revokedAt() == null) {
                    store.saveSession(s.revoke(now));
                }
            });
            return null;
        });
    }

    /** Devuelve el customerId de una sesión activa de un cliente validado. */
    public Optional<String> authenticate(String token) {
        Instant now = clock.instant();
        return store.findSessionByTokenHash(CredentialHasher.sha256(token))
                .filter(s -> s.activeAt(now))
                .flatMap(s -> store.findCustomer(s.customerId()))
                .filter(c -> c.status() == CustomerStatus.VALIDATED)
                .map(c -> c.id().toString());
    }

    private void registerFailure(String key, Instant now) {
        failures.merge(key, new Failures(1, null), (old, ignored) -> {
            int count = old.count() + 1;
            return count >= maxFailed ? new Failures(0, now.plusSeconds(lockoutSeconds)) : new Failures(count, null);
        });
    }
}
