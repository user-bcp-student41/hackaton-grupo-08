import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { GENERIC_ERROR, ERROR_MESSAGES } from './payment-labels';
import { newIdempotencyKey, PaymentsApi } from './payments.api';
import { ApiError, PaymentRequest, ValidationResult } from './payments.models';

/** Proveedor/canal de la demo (coinciden con `payments.limits.*` de application.properties). */
export const DEMO_PROVIDER_ID = 'mock-provider-a';
export const DEMO_CHANNEL = 'ALIAS';

type Step = 'capture' | 'review';

/**
 * Captura y confirmación del pago (HU2-AC1). Antes de confirmar se muestra el destinatario y el importe;
 * no se crea ningún intento hasta pulsar "Confirmar". La clave de idempotencia se genera en la primera
 * confirmación y se reutiliza si se reenvía la MISMA operación (doble clic, error de red).
 */
@Component({
  selector: 'app-payment-form',
  template: `
    @if (step() === 'capture') {
      <form (submit)="review($event)" novalidate data-testid="payment-capture">
        <label>
          Destinatario
          <input name="recipientRef" [value]="recipientRef()" (input)="recipientRef.set(value($event))" />
        </label>
        <label>
          Importe (PEN)
          <input name="amount" inputmode="decimal" [value]="amountText()" (input)="amountText.set(value($event))" />
        </label>
        @if (error()) {
          <p role="alert" data-testid="payment-error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="busy()">Revisar pago</button>
      </form>
    } @else {
      <section data-testid="payment-review" aria-label="Confirmar pago">
        <h2>Confirma tu pago</h2>
        <dl>
          <dt>Destinatario</dt>
          <dd data-testid="review-recipient">
            {{ review_()?.recipient?.displayName }} ({{ review_()?.recipient?.maskedAlias }})
          </dd>
          <dt>Importe</dt>
          <dd data-testid="review-amount">{{ review_()?.currency }} {{ formattedAmount() }}</dd>
        </dl>
        @if (error()) {
          <p role="alert" data-testid="payment-error">{{ error() }}</p>
        }
        <button type="button" data-testid="confirm" [disabled]="busy()" (click)="confirm()">Confirmar</button>
        <button type="button" [disabled]="busy()" (click)="edit()">Editar</button>
      </section>
    }
  `,
})
export class PaymentFormComponent implements OnInit {
  private readonly api = inject(PaymentsApi);
  private readonly router = inject(Router);

  protected readonly step = signal<Step>('capture');
  protected readonly recipientRef = signal('');
  protected readonly amountText = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly review_ = signal<ValidationResult | null>(null);
  protected readonly formattedAmount = computed(() => {
    const amount = this.review_()?.amount;
    return amount === undefined ? '' : amount.toFixed(2);
  });

  private request: PaymentRequest | null = null;
  private idempotencyKey: string | null = null;

  ngOnInit(): void {
    this.api.trackEvent('CHECKOUT_CONFIRMATION_VIEWED');
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected review(event: Event): void {
    event.preventDefault();
    const ref = this.recipientRef().trim();
    const amount = this.amountText().trim().replace(',', '.');
    if (!ref || !/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) {
      this.error.set(ERROR_MESSAGES['INVALID_REQUEST']);
      return;
    }
    const request: PaymentRequest = {
      recipientRef: ref,
      amount: Number(amount),
      currency: 'PEN',
      providerId: DEMO_PROVIDER_ID,
      channel: DEMO_CHANNEL,
    };
    this.busy.set(true);
    this.error.set(null);
    this.api.validate(request).subscribe({
      next: (result) => {
        this.request = request;
        this.idempotencyKey = null; // operación nueva: clave nueva en la primera confirmación
        this.review_.set(result);
        this.step.set('review');
        this.busy.set(false);
      },
      error: (failure: ApiError) => this.fail(failure),
    });
  }

  protected edit(): void {
    this.request = null;
    this.idempotencyKey = null;
    this.error.set(null);
    this.step.set('capture');
  }

  protected confirm(): void {
    if (this.busy() || !this.request) {
      return;
    }
    this.idempotencyKey ??= newIdempotencyKey();
    this.busy.set(true);
    this.error.set(null);
    this.api.create(this.request, this.idempotencyKey).subscribe({
      next: (payment) => {
        this.idempotencyKey = null;
        this.busy.set(false);
        void this.router.navigate(['/payments', payment.paymentId]);
      },
      error: (failure: ApiError) => this.fail(failure),
    });
  }

  private fail(failure: ApiError): void {
    this.busy.set(false);
    const code = failure.error?.code;
    this.error.set((code && ERROR_MESSAGES[code]) || GENERIC_ERROR);
    // Un rechazo de negocio (4xx) significa que la operación cambió: la próxima confirmación usa clave nueva.
    // Un fallo de red/5xx conserva la clave para no duplicar el cobro al reenviar la misma operación.
    if (failure.status !== undefined && failure.status >= 400 && failure.status < 500) {
      this.idempotencyKey = null;
    }
  }
}
