package com.ailabs.payments.config;

import io.smallrye.config.ConfigMapping;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Configuración de pagos. SIN valores por defecto inventados (preguntas abiertas P8 y P9): la expiración del
 * {@code PENDING} es obligatoria y un combo proveedor/canal sin límite configurado es "no soportado".
 * Los valores de los perfiles {@code %dev} y {@code %test} son solo de demostración/pruebas.
 *
 * <pre>
 * payments.pending-expiration=PT...
 * payments.limits.&lt;providerId&gt;.&lt;channel&gt;.max-amount=...
 * </pre>
 */
@ConfigMapping(prefix = "payments")
public interface PaymentsConfig {

    Duration pendingExpiration();

    Map<String, Map<String, ChannelLimit>> limits();

    interface ChannelLimit {
        BigDecimal maxAmount();
    }
}
