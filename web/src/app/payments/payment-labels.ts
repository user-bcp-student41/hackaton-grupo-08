import { PaymentStatus } from './payments.models';

/**
 * Etiquetas PROVISIONALES visibles al usuario. Producto aún no las validó (pregunta abierta): se concentran
 * aquí para cambiarlas en un solo lugar y se muestran con el aviso {@link PROVISIONAL_NOTICE}.
 */
export const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Procesando',
  SUCCESS: 'Exitoso',
  FAILED: 'Fallido',
  EXPIRED: 'Expirado',
};

export const PROVISIONAL_NOTICE = 'Etiqueta provisional, pendiente de validación por Producto.';

/** Mensajes por `code` de error de la API; nunca se muestra texto técnico ni el valor del límite. */
export const ERROR_MESSAGES: Record<string, string> = {
  AMOUNT_LIMIT_EXCEEDED: 'El monto supera el máximo permitido para este canal.',
  RECIPIENT_NOT_FOUND: 'No encontramos al destinatario. Revisa el dato ingresado.',
  WALLET_NOT_ACTIVE: 'Tu wallet aún no está activa. Completa el onboarding para pagar.',
  PROVIDER_UNAVAILABLE: 'El proveedor no está disponible en este momento. Inténtalo de nuevo en unos minutos.',
  PROVIDER_CHANNEL_UNSUPPORTED: 'El canal de pago no está disponible.',
  IDEMPOTENCY_KEY_CONFLICT: 'Esta operación cambió. Vuelve a revisar el pago.',
  PAYMENT_NOT_RETRYABLE: 'Este pago ya no se puede reintentar.',
  PROVIDER_STATE_UNRESOLVED: 'Aún estamos verificando el pago anterior. Consulta tu historial en unos minutos.',
  INVALID_REQUEST: 'Revisa el destinatario y el importe: el monto debe ser mayor que cero y tener máximo 2 decimales.',
};

export const GENERIC_ERROR = 'No pudimos completar la operación. Inténtalo de nuevo.';
