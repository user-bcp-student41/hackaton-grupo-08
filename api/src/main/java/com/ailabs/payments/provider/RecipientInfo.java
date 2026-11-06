package com.ailabs.payments.provider;

/** Identidad visible del destinatario devuelta por el proveedor (o enmascarada por el servicio). */
public record RecipientInfo(String displayName, String maskedAlias) {
}
