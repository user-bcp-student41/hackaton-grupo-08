package com.ailabs.onboarding.domain;

/**
 * Estados de la wallet. {@code ACTIVE} está confirmado por la HU; {@code PENDING} (estado previo a activar)
 * es un supuesto alineado con el estado PENDING del mock (pendiente de confirmar, Pregunta 8).
 */
public enum WalletStatus {
    PENDING,
    ACTIVE
}
