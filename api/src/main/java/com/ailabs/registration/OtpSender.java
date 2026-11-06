package com.ailabs.registration;

/** Puerto del canal de correo (EP-HU1_2-03). La implementación incluida es simulada. */
public interface OtpSender {

    /** @throws OtpDeliveryException si el canal falla. */
    void send(String email, String code);

    class OtpDeliveryException extends RuntimeException {
        public OtpDeliveryException(String message) {
            super(message);
        }
    }
}
