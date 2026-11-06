export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';

export interface Recipient {
  displayName: string;
  maskedAlias: string;
}

export interface PaymentRequest {
  recipientRef: string;
  amount: number;
  currency: 'PEN';
  providerId: string;
  channel: string;
}

export interface ValidationResult {
  valid: boolean;
  recipient: Recipient;
  amount: number;
  currency: string;
}

export interface Payment {
  paymentId: string;
  status: PaymentStatus;
  terminal: boolean;
  retryable: boolean;
  amount: number;
  currency: string;
  recipient: Recipient;
  providerId: string;
  channel: string;
  transactionReference: string | null;
  failureCode: string | null;
  retryOf: string | null;
  createdAt: string;
  terminalAt: string | null;
}

export interface HistoryItem {
  paymentId: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  recipient: Recipient;
  transactionReference: string | null;
  createdAt: string;
  terminalAt: string | null;
}

export interface PaymentHistory {
  items: HistoryItem[];
  page: number;
  size: number;
  totalItems: number;
}

export type CheckoutUiEvent = 'CHECKOUT_CONFIRMATION_VIEWED' | 'CHECKOUT_PENDING_SHOWN';

/** Cuerpo de error común de la API (`code`, `message`, `details`). */
export interface ApiError {
  status?: number;
  error?: { code?: string; message?: string; details?: Record<string, unknown> };
}
