package com.ailabs.onboarding.service;

import com.ailabs.onboarding.domain.OnboardingSession;

public record StartResult(OnboardingSession session, boolean created) {
}
