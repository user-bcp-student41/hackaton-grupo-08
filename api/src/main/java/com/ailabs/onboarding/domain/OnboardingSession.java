package com.ailabs.onboarding.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sesión de onboarding inmutable. Cada transición devuelve una copia con {@code version} incrementada,
 * que se usa para el control de concurrencia optimista.
 */
public record OnboardingSession(
        UUID id,
        String userId,
        String walletId,
        SessionStatus status,
        String acquisitionSource,
        Instant startedAt,
        Instant completedAt,
        int version,
        List<StepProgress> steps) {

    public OnboardingSession {
        steps = List.copyOf(steps);
    }

    /** Código del paso en curso, o {@code null} cuando ya no quedan pasos por enviar. */
    public String currentStepCode() {
        return steps.stream()
                .filter(step -> step.status() == StepStatus.IN_PROGRESS)
                .map(StepProgress::stepCode)
                .findFirst()
                .orElse(null);
    }

    public Optional<StepProgress> step(String stepCode) {
        return steps.stream().filter(step -> step.stepCode().equals(stepCode)).findFirst();
    }

    public List<String> pendingSteps() {
        return steps.stream()
                .filter(step -> step.status() != StepStatus.COMPLETED)
                .map(StepProgress::stepCode)
                .toList();
    }

    public boolean readyToComplete() {
        return status == SessionStatus.IN_PROGRESS && pendingSteps().isEmpty();
    }

    public OnboardingSession completeStep(String stepCode, Instant at) {
        List<StepProgress> updated = new ArrayList<>();
        boolean justCompleted = false;
        boolean promoted = false;
        for (StepProgress step : steps) {
            if (step.stepCode().equals(stepCode)) {
                updated.add(new StepProgress(step.stepCode(), step.order(), StepStatus.COMPLETED, at));
                justCompleted = true;
            } else if (justCompleted && !promoted && step.status() == StepStatus.PENDING) {
                updated.add(new StepProgress(step.stepCode(), step.order(), StepStatus.IN_PROGRESS, null));
                promoted = true;
            } else {
                updated.add(step);
            }
        }
        return new OnboardingSession(id, userId, walletId, status, acquisitionSource, startedAt, completedAt,
                version + 1, updated);
    }

    public OnboardingSession complete(Instant at) {
        return new OnboardingSession(id, userId, walletId, SessionStatus.COMPLETED, acquisitionSource, startedAt,
                at, version + 1, steps);
    }
}
