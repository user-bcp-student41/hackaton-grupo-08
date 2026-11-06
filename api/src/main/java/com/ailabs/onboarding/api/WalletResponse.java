package com.ailabs.onboarding.api;

import com.ailabs.onboarding.domain.Wallet;

public record WalletResponse(String walletId, String status) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.walletId(), wallet.status().name());
    }
}
