package com.ailabs.payments.service;

import com.ailabs.payments.config.PaymentsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * B3: límite máximo por (providerId, channel), solo por configuración. El límite es inclusivo (S7): un importe
 * igual al máximo se acepta. Un combo no configurado es "no soportado".
 */
@ApplicationScoped
public class ProviderChannelLimitPolicy {

    private final PaymentsConfig config;

    @Inject
    public ProviderChannelLimitPolicy(PaymentsConfig config) {
        this.config = config;
    }

    public Optional<BigDecimal> maxAmount(String providerId, String channel) {
        Map<String, PaymentsConfig.ChannelLimit> byChannel = config.limits().get(providerId);
        if (byChannel == null) {
            return Optional.empty();
        }
        PaymentsConfig.ChannelLimit limit = byChannel.get(channel);
        return limit == null ? Optional.empty() : Optional.ofNullable(limit.maxAmount());
    }
}
