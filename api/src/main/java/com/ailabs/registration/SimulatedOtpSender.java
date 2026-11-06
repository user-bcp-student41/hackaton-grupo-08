package com.ailabs.registration;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Envío de correo SIMULADO: no sale ningún correo real. El código solo queda en un buzón en memoria cuando
 * {@code registration.mailbox.enabled=true} (perfiles de prueba/desarrollo); nunca en logs ni respuestas.
 */
@ApplicationScoped
public class SimulatedOtpSender implements OtpSender {

    private static final Logger LOG = Logger.getLogger(SimulatedOtpSender.class);

    private final Map<String, String> mailbox = new ConcurrentHashMap<>();

    @ConfigProperty(name = "registration.mailbox.enabled", defaultValue = "false")
    boolean mailboxEnabled;

    @Override
    public void send(String email, String code) {
        if (mailboxEnabled) {
            mailbox.put(email, code);
        }
        LOG.info("OTP enviado (canal de correo simulado)");
    }

    /** Solo disponible con el buzón habilitado (pruebas). */
    public Optional<String> lastCode(String email) {
        return Optional.ofNullable(mailbox.get(email));
    }
}
