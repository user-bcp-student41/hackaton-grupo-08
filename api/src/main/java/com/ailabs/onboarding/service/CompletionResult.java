package com.ailabs.onboarding.service;

import com.ailabs.onboarding.domain.OnboardingSession;
import com.ailabs.onboarding.domain.Wallet;

public record CompletionResult(OnboardingSession session, Wallet wallet) {
}
