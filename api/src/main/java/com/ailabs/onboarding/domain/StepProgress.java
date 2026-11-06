package com.ailabs.onboarding.domain;

import java.time.Instant;

public record StepProgress(String stepCode, int order, StepStatus status, Instant completedAt) {
}
