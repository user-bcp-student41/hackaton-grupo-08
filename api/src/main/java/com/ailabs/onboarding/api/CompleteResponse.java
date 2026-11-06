package com.ailabs.onboarding.api;

import java.time.Instant;
import java.util.UUID;

public record CompleteResponse(
        UUID sessionId,
        String status,
        Instant completedAt,
        WalletResponse wallet,
        String paymentsEntryPoint) {
}
