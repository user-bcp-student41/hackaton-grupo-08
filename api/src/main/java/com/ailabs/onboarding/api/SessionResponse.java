package com.ailabs.onboarding.api;

import com.ailabs.onboarding.domain.OnboardingSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        String status,
        String currentStepCode,
        boolean readyToComplete,
        Instant startedAt,
        Instant completedAt,
        List<StepResponse> steps) {

    public record StepResponse(String stepCode, int order, String status) {
    }

    public static SessionResponse from(OnboardingSession session) {
        List<StepResponse> steps = session.steps().stream()
                .map(step -> new StepResponse(step.stepCode(), step.order(), step.status().name()))
                .toList();
        return new SessionResponse(session.id(), session.status().name(), session.currentStepCode(),
                session.readyToComplete(), session.startedAt(), session.completedAt(), steps);
    }
}
