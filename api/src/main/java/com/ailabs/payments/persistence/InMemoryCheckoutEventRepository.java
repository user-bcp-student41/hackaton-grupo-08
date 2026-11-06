package com.ailabs.payments.persistence;

import com.ailabs.payments.domain.CheckoutEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Implementación en memoria (NO durable) de {@link CheckoutEventRepository}. */
@ApplicationScoped
public class InMemoryCheckoutEventRepository implements CheckoutEventRepository {

    private final List<CheckoutEvent> events = new ArrayList<>();
    private final Set<String> dedupeKeys = new HashSet<>();

    @Override
    public synchronized boolean append(CheckoutEvent event) {
        if (event.dedupeKey() != null && !dedupeKeys.add(event.eventType() + "|" + event.dedupeKey())) {
            return false;
        }
        events.add(event);
        return true;
    }

    @Override
    public synchronized List<CheckoutEvent> findAll() {
        return List.copyOf(events);
    }
}
