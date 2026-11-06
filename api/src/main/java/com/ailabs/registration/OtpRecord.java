package com.ailabs.registration;

import java.time.Instant;

/** OTP vigente de un cliente: solo hash, vencimiento, intentos fallidos y reenvíos. */
public record OtpRecord(String otpHash, Instant expiresAt, int attempts, int resends) {

    public OtpRecord withAttempt() {
        return new OtpRecord(otpHash, expiresAt, attempts + 1, resends);
    }

    @Override
    public String toString() {
        return "OtpRecord[attempts=" + attempts + ", resends=" + resends + "]";
    }
}
