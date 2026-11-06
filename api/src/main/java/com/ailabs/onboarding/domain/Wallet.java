package com.ailabs.onboarding.domain;

import java.time.Instant;

public record Wallet(String walletId, String userId, WalletStatus status, Instant activatedAt) {

    public Wallet activate(Instant at) {
        return new Wallet(walletId, userId, WalletStatus.ACTIVE, at);
    }
}
