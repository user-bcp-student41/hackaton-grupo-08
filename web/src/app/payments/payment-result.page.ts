import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ERROR_MESSAGES, GENERIC_ERROR, PROVISIONAL_NOTICE, STATUS_LABELS } from './payment-labels';
import { newIdempotencyKey, PaymentsApi } from './payments.api';
import { ApiError, Payment } from './payments.models';

/** Retroceso acotado entre consultas (ms) y tope de consultas automáticas. */
export const POLL_DELAYS_MS = [1000, 2000, 3000, 5000];
export const MAX_POLLS = 20;

/**
 * Resultado del intento (HU2-AC2..AC4). PENDING se muestra como "procesando", sin etiqueta terminal ni
 * referencia ni "Reintentar". Al volverse terminal se detiene el sondeo y se muestra un único resultado.
 */
@Component({
  selector: 'app-payment-result-page',
  imports: [RouterLink],
  template: `
    <main class="payments">
      <h1>Resultado del pago</h1>
      @if (payment(); as p) {
        <dl>
          <dt>Destinatario</dt>
          <dd>{{ p.recipient.displayName }} ({{ p.recipient.maskedAlias }})</dd>
          <dt>Importe</dt>
          <dd>{{ p.currency }} {{ p.amount.toFixed(2) }}</dd>
        </dl>
        @if (!p.terminal) {
          <p role="status" data-testid="payment-pending">
            {{ labels.PENDING }}… <small>{{ notice }}</small>
          </p>
          @if (gaveUp()) {
            <p data-testid="payment-still-processing">
              Sigue procesando. Puedes salir: consulta el resultado en tu historial.
            </p>
          }
        } @else {
          <p role="status" data-testid="payment-terminal" [attr.data-status]="p.status">
            <strong>{{ labels[p.status] }}</strong> <small>{{ notice }}</small>
          </p>
          @if (p.status === 'SUCCESS' && p.transactionReference) {
            <p data-testid="payment-reference">Referencia: {{ p.transactionReference }}</p>
          }
        }
        @if (error()) {
          <p role="alert" data-testid="payment-error">{{ error() }}</p>
        }
        @if (p.retryable) {
          <button type="button" data-testid="retry" [disabled]="busy()" (click)="retry(p)">Reintentar</button>
        }
        @if (p.terminal) {
          <a routerLink="/payments/new" data-testid="back">Volver</a>
        }
        <a routerLink="/payments" data-testid="to-history">Ver historial</a>
      } @else if (error()) {
        <p role="alert" data-testid="payment-error">{{ error() }}</p>
        <a routerLink="/payments/new">Volver</a>
      } @else {
        <p role="status">Cargando…</p>
      }
    </main>
  `,
})
export class PaymentResultPage implements OnInit, OnDestroy {
  private readonly api = inject(PaymentsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly labels = STATUS_LABELS;
  protected readonly notice = PROVISIONAL_NOTICE;
  protected readonly payment = signal<Payment | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly gaveUp = signal(false);

  private paymentId = '';
  private polls = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pendingShownTracked = false;
  private retryKey: string | null = null;

  ngOnInit(): void {
    this.paymentId = this.route.snapshot.paramMap.get('paymentId') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private load(): void {
    this.api.get(this.paymentId).subscribe({
      next: (payment) => this.onPayment(payment),
      error: (failure: ApiError) => this.onLoadError(failure),
    });
  }

  private onPayment(payment: Payment): void {
    // El estado terminal es inmutable: una respuesta posterior nunca lo reemplaza.
    if (this.payment()?.terminal) {
      return;
    }
    this.payment.set(payment);
    if (payment.terminal) {
      this.stop();
      return;
    }
    if (!this.pendingShownTracked) {
      this.pendingShownTracked = true;
      this.api.trackEvent('CHECKOUT_PENDING_SHOWN', payment.paymentId);
    }
    this.schedule();
  }

  private onLoadError(failure: ApiError): void {
    if (this.payment()) {
      this.schedule(); // fallo transitorio de consulta: seguimos mostrando "procesando" y reintentamos
      return;
    }
    this.error.set(failure.status === 404 ? 'No encontramos este pago.' : GENERIC_ERROR);
  }

  private schedule(): void {
    this.stop();
    if (this.polls >= MAX_POLLS) {
      this.gaveUp.set(true);
      return;
    }
    const delay = POLL_DELAYS_MS[Math.min(this.polls, POLL_DELAYS_MS.length - 1)];
    this.polls += 1;
    this.timer = setTimeout(() => this.load(), delay);
  }

  private stop(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  /** Reintento: una clave nueva por acción del usuario, reutilizada solo si se reenvía esa misma acción. */
  protected retry(payment: Payment): void {
    if (this.busy()) {
      return;
    }
    this.retryKey ??= newIdempotencyKey();
    this.busy.set(true);
    this.error.set(null);
    this.api.retry(payment.paymentId, this.retryKey).subscribe({
      next: (created) => {
        this.retryKey = null;
        this.busy.set(false);
        void this.router.navigate(['/payments', created.paymentId]);
      },
      error: (failure: ApiError) => {
        this.busy.set(false);
        const code = failure.error?.code;
        this.error.set((code && ERROR_MESSAGES[code]) || GENERIC_ERROR);
        if (failure.status !== undefined && failure.status >= 400 && failure.status < 500) {
          this.retryKey = null;
        }
      },
    });
  }
}
