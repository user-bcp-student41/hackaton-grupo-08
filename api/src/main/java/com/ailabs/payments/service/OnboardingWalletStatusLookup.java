package com.ailabs.payments.service;

import com.ailabs.onboarding.api.ApiException;
import com.ailabs.onboarding.domain.Wallet;
import com.ailabs.onboarding.domain.WalletStatus;
import com.ailabs.onboarding.service.OnboardingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/** Adaptador sobre {@link OnboardingService#wallet(String)}: una wallet inexistente cuenta como no activa (P12). */
@ApplicationScoped
public class OnboardingWalletStatusLookup implements WalletStatusLookup {

    private final OnboardingService onboarding;

    @Inject
    public OnboardingWalletStatusLookup(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @Override
    public Optional<String> activeWalletId(String userId) {
        try {
            Wallet wallet = onboarding.wallet(userId);
            return wallet.status() == WalletStatus.ACTIVE ? Optional.of(wallet.walletId()) : Optional.empty();
        } catch (ApiException notFound) {
            if (notFound.status() == 404) {
                return Optional.empty();
            }
            throw notFound;
        }
    }
}
