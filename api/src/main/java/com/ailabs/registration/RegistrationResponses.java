package com.ailabs.registration;

import com.ailabs.onboarding.api.WalletResponse;
import com.ailabs.onboarding.domain.StepStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuestas de registro (sin OTP, contraseña, hash ni documento). */
public final class RegistrationResponses {

    private RegistrationResponses() {
    }

    public record StepView(String stepCode, int order, String status) {
    }

    public record RegistrationView(UUID registrationId, String customerStatus, Integer currentStep, int totalSteps,
            List<StepView> steps, Instant startedAt, Instant completedAt) {

        public static RegistrationView of(Registration reg, Customer customer) {
            boolean done = reg.completed() && customer.status() == CustomerStatus.VALIDATED;
            List<StepView> steps = List.of(
                    new StepView(Registration.STEP_PERSONAL_DATA, 1, StepStatus.COMPLETED.name()),
                    new StepView(Registration.STEP_OTP, 2,
                            (done ? StepStatus.COMPLETED : StepStatus.IN_PROGRESS).name()));
            return new RegistrationView(reg.id(), customer.status().name(), done ? null : 2, 2, steps,
                    reg.startedAt(), reg.completedAt());
        }
    }

    public record VerifyView(UUID registrationId, String customerStatus, boolean onboardingCompleted,
            Instant completedAt, WalletResponse wallet, String paymentsEntryPoint) {
    }

    public record LoginView(String accessToken, String tokenType, Instant expiresAt, String walletStatus,
            String paymentsEntryPoint) {
    }
}
