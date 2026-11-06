package com.ailabs.payments.persistence;

import com.ailabs.payments.domain.CheckoutEvent;
import java.util.List;

/** Almacén append-only de eventos de checkout (paralelo a {@code AnalyticsEventRepository} de onboarding). */
public interface CheckoutEventRepository {

    /**
     * Agrega el evento. Si {@code dedupeKey} no es nulo y ya existe, no agrega nada.
     *
     * @return {@code true} si se agregó; {@code false} si era un duplicado.
     */
    boolean append(CheckoutEvent event);

    List<CheckoutEvent> findAll();
}
