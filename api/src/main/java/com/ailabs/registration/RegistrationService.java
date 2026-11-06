package com.ailabs.registration;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.api.WalletResponse;
import com.ailabs.onboarding.domain.AnalyticsEvent;
import com.ailabs.onboarding.domain.EventType;
import com.ailabs.onboarding.domain.Wallet;
import com.ailabs.onboarding.domain.WalletStatus;
import com.ailabs.onboarding.persistence.AnalyticsEventRepository;
import com.ailabs.onboarding.persistence.OnboardingStore;
import com.ailabs.onboarding.service.OnboardingService;
import com.ailabs.registration.RegistrationResponses.RegistrationView;
import com.ailabs.registration.RegistrationResponses.VerifyView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Orquesta Paso 1 (datos) y Paso 2 (OTP). Único que cambia el estado del cliente y de la wallet en el registro. */
@ApplicationScoped
public class RegistrationService {

    private static final Logger LOG = Logger.getLogger(RegistrationService.class);

    @Inject
    CustomerStore store;
    @Inject
    OnboardingStore onboardingStore;
    @Inject
    AnalyticsEventRepository events;
    @Inject
    CredentialHasher hasher;
    @Inject
    OtpSender sender;
    @Inject
    Clock clock;

    @ConfigProperty(name = "registration.otp.length")
    int otpLength;
    @ConfigProperty(name = "registration.otp.ttl-seconds")
    long otpTtlSeconds;
    @ConfigProperty(name = "registration.otp.max-attempts")
    int maxAttempts;
    @ConfigProperty(name = "registration.otp.max-resends")
    int maxResends;
    /** Solo para desarrollo sin servicio de correo: si está definido, todos los OTP emitidos son este valor. */
    @ConfigProperty(name = "registration.otp.fixed-code")
    Optional<String> fixedCode;
    @ConfigProperty(name = "onboarding.payments-entry-point", defaultValue = "/payments/new")
    String paymentsEntryPoint;

    private final SecureRandom random = new SecureRandom();

    public RegistrationView register(RegisterRequest req) {
        List<FieldIssue> issues = new ArrayList<>();
        if (req == null) {
            throw ApiException.badRequest("Envía los datos del registro.");
        }
        FieldRules.documentType(req.documentType(), issues);
        FieldRules.documentNumber(req.documentNumber(), issues);
        FieldRules.email(req.email(), issues);
        FieldRules.password(req.password(), issues);
        if (!FieldRules.validSource(req.acquisitionSource())) {
            issues.add(new FieldIssue("acquisitionSource", "INVALID_FORMAT", "La fuente de adquisición no es válida."));
        }
        if (!issues.isEmpty()) {
            throw ApiException.validation(Registration.STEP_PERSONAL_DATA, issues);
        }
        String passwordHash = hasher.hash(req.password());
        String source = req.acquisitionSource() == null ? OnboardingService.UNATTRIBUTED : req.acquisitionSource();
        String code = newCode();
        String otpHash = hasher.hash(code);
        Instant now = clock.instant();

        Object[] created = store.withLock(() -> {
            var existing = store.findByDocument(req.documentType(), req.documentNumber());
            if (existing.isPresent() && existing.get().status() == CustomerStatus.VALIDATED) {
                throw RegistrationErrors.documentAlreadyRegistered();
            }
            UUID customerId = existing.map(Customer::id).orElseGet(UUID::randomUUID);
            Instant createdAt = existing.map(Customer::createdAt).orElse(now);
            Customer customer = new Customer(customerId, req.documentType(), req.documentNumber(), req.email(),
                    CustomerStatus.UNVALIDATED, passwordHash, createdAt);
            store.saveCustomer(customer);
            store.deleteRegistrationsOf(customerId);
            Registration reg = new Registration(UUID.randomUUID(), customerId, source, now, null);
            store.saveRegistration(reg);
            String userId = customerId.toString();
            Wallet wallet = onboardingStore.findWalletByUser(userId)
                    .orElseGet(() -> new Wallet("w-" + UUID.randomUUID(), userId, WalletStatus.PENDING, null));
            onboardingStore.saveWallet(wallet);
            store.saveOtp(customerId, new OtpRecord(otpHash, now.plusSeconds(otpTtlSeconds), 0, 0));
            return new Object[] { reg, customer };
        });
        Registration reg = (Registration) created[0];
        Customer customer = (Customer) created[1];

        record(started(reg, customer, now));
        record(step(reg, customer, Registration.STEP_PERSONAL_DATA, now));
        deliver(customer.email(), code);
        return RegistrationView.of(reg, customer);
    }

    public RegistrationView get(UUID registrationId) {
        Registration reg = store.findRegistration(registrationId).orElseThrow(RegistrationErrors::registrationNotFound);
        Customer customer = store.findCustomer(reg.customerId()).orElseThrow(RegistrationErrors::registrationNotFound);
        return RegistrationView.of(reg, customer);
    }

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    public VerifyView verify(UUID registrationId, VerifyOtpRequest req) {
        List<FieldIssue> issues = new ArrayList<>();
        String otp = req == null ? null : req.otp();
        if (otp == null || otp.isEmpty()) {
            issues.add(new FieldIssue("otp", "REQUIRED", "Ingresa el código que enviamos a tu correo."));
        } else if (otp.length() != otpLength || !DIGITS.matcher(otp).matches()) {
            issues.add(new FieldIssue("otp", "INVALID_FORMAT", "El código tiene " + otpLength + " dígitos."));
        }
        if (!issues.isEmpty()) {
            throw ApiException.validation(Registration.STEP_OTP, issues);
        }
        Instant now = clock.instant();
        Object[] outcome = store.withLock(() -> {
            Registration reg = store.findRegistration(registrationId).orElseThrow(RegistrationErrors::registrationNotFound);
            Customer customer = store.findCustomer(reg.customerId()).orElseThrow(RegistrationErrors::registrationNotFound);
            String userId = customer.id().toString();
            if (customer.status() == CustomerStatus.VALIDATED) {
                return new Object[] { reg, walletOf(userId), Boolean.FALSE, customer };
            }
            OtpRecord record = store.findOtp(customer.id()).orElse(null);
            if (record == null || !now.isBefore(record.expiresAt())) {
                throw RegistrationErrors.otpExpired();
            }
            if (record.attempts() >= maxAttempts) {
                throw RegistrationErrors.otpAttemptsExceeded();
            }
            if (!hasher.matches(otp, record.otpHash())) {
                store.saveOtp(customer.id(), record.withAttempt());
                throw RegistrationErrors.otpInvalid();
            }
            store.deleteOtp(customer.id());
            Customer validated = customer.validated();
            store.saveCustomer(validated);
            Wallet active = walletOf(userId).activate(now);
            onboardingStore.saveWallet(active);
            Registration done = reg.complete(now);
            store.saveRegistration(done);
            return new Object[] { done, active, Boolean.TRUE, validated };
        });
        Registration reg = (Registration) outcome[0];
        Wallet wallet = (Wallet) outcome[1];
        Customer customer = (Customer) outcome[3];
        if (Boolean.TRUE.equals(outcome[2])) {
            record(step(reg, customer, Registration.STEP_OTP, now));
            record(new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_CLOSED, now, customer.id().toString(),
                    reg.id(), null, AnalyticsEvent.OUTCOME_COMPLETED, reg.acquisitionSource()));
        }
        return new VerifyView(reg.id(), CustomerStatus.VALIDATED.name(), true, reg.completedAt(),
                WalletResponse.from(wallet), paymentsEntryPoint);
    }

    public void resend(UUID registrationId) {
        String code = newCode();
        String otpHash = hasher.hash(code);
        Instant now = clock.instant();
        String email = store.withLock(() -> {
            Registration reg = store.findRegistration(registrationId).orElseThrow(RegistrationErrors::registrationNotFound);
            Customer customer = store.findCustomer(reg.customerId()).orElseThrow(RegistrationErrors::registrationNotFound);
            if (customer.status() == CustomerStatus.VALIDATED) {
                throw RegistrationErrors.alreadyCompleted();
            }
            int resends = store.findOtp(customer.id()).map(OtpRecord::resends).orElse(0);
            if (resends >= maxResends) {
                throw RegistrationErrors.otpResendLimit();
            }
            store.saveOtp(customer.id(), new OtpRecord(otpHash, now.plusSeconds(otpTtlSeconds), 0, resends + 1));
            return customer.email();
        });
        deliver(email, code);
    }

    private Wallet walletOf(String userId) {
        return onboardingStore.findWalletByUser(userId).orElseThrow(ApiException::internal);
    }

    private void deliver(String email, String code) {
        try {
            sender.send(email, code);
        } catch (RuntimeException e) {
            LOG.warn("Falló el envío simulado del OTP");
            throw RegistrationErrors.otpDeliveryUnavailable();
        }
    }

    private String newCode() {
        if (fixedCode.isPresent() && !fixedCode.get().isBlank()) {
            return fixedCode.get().trim();
        }
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private AnalyticsEvent started(Registration reg, Customer customer, Instant at) {
        return new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_STARTED, at, customer.id().toString(),
                reg.id(), null, null, reg.acquisitionSource());
    }

    private AnalyticsEvent step(Registration reg, Customer customer, String stepCode, Instant at) {
        return new AnalyticsEvent(UUID.randomUUID(), EventType.ONBOARDING_STEP_COMPLETED, at,
                customer.id().toString(), reg.id(), stepCode, null, reg.acquisitionSource());
    }

    /** D9: un fallo al registrar el evento no altera la respuesta funcional. */
    private void record(AnalyticsEvent event) {
        try {
            events.append(event);
        } catch (RuntimeException e) {
            LOG.warnf("No se pudo registrar el evento %s", event.eventType());
        }
    }
}
