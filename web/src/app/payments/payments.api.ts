import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CheckoutUiEvent,
  Payment,
  PaymentHistory,
  PaymentRequest,
  ValidationResult,
} from './payments.models';

const BASE = '/api/v1';

/** Genera una clave de idempotencia UUID (la API exige UUID). */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly http = inject(HttpClient);

  /** Pre-valida destinatario, importe y límite sin crear ningún intento. */
  validate(request: PaymentRequest): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${BASE}/payments/validate`, request);
  }

  /** Crea el intento. La misma clave en un reenvío de la misma operación devuelve el mismo pago. */
  create(request: PaymentRequest, idempotencyKey: string): Observable<Payment> {
    return this.http.post<Payment>(`${BASE}/payments`, request, { headers: this.keyHeader(idempotencyKey) });
  }

  get(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${BASE}/payments/${encodeURIComponent(paymentId)}`);
  }

  /** Reintento: nuevo intento con clave nueva por acción del usuario; el anterior no se modifica. */
  retry(paymentId: string, idempotencyKey: string): Observable<Payment> {
    return this.http.post<Payment>(`${BASE}/payments/${encodeURIComponent(paymentId)}/retry`, null, {
      headers: this.keyHeader(idempotencyKey),
    });
  }

  history(page = 0, size = 20): Observable<PaymentHistory> {
    return this.http.get<PaymentHistory>(`${BASE}/payments`, {
      params: { page: String(page), size: String(size) },
    });
  }

  /** Hitos de UI del funnel. Es "best effort": un fallo nunca debe afectar al flujo de pago. */
  trackEvent(eventType: CheckoutUiEvent, paymentId?: string): void {
    const body = paymentId ? { eventType, paymentId } : { eventType };
    this.http.post(`${BASE}/checkout-events`, body).subscribe({ error: () => undefined });
  }

  private keyHeader(key: string): HttpHeaders {
    return new HttpHeaders({ 'Idempotency-Key': key });
  }
}
