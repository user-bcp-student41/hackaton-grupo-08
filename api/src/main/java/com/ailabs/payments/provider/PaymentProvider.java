package com.ailabs.payments.provider;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto que aísla el riel de pago (EP-HU1_2-03): la lógica de pago no conoce ningún proveedor concreto.
 * Contrato mínimo decidido en la arquitectura (sección 3.1).
 */
public interface PaymentProvider {

    /**
     * @return la identidad visible, o vacío si el destinatario no existe.
     * @throws ProviderUnavailableException si el proveedor no responde.
     */
    Optional<RecipientInfo> resolveRecipient(String recipientRef, String providerId, String channel);

    /** Envía el cobro. Nunca lanza por incertidumbre: devuelve {@link SubmitOutcome.Kind#UNKNOWN}. */
    SubmitOutcome submit(SubmitRequest request);

    /** Consulta el estado de un cobro ya enviado, sin crear otro cobro. */
    ProviderStatus queryStatus(UUID paymentId, String providerId);
}
