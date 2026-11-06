import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { money } from '../core/format';
import { IconComponent } from '../core/ui/icon.component';
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
  imports: [IconComponent],
  template: `
    @if (step() === 'capture') {
      <h2 class="heading">¿A quién le pagas?</h2>
      <p class="lead">Ingresa el contacto y el importe. Podrás revisar todo antes de confirmar.</p>
      <form (submit)="review($event)" novalidate data-testid="payment-capture">
        <div class="field">
          <label class="field__label" for="recipientRef">Destinatario</label>
          <div class="control">
            <span class="control__icon"><app-icon name="user" /></span>
            <input id="recipientRef" name="recipientRef" autocomplete="off" placeholder="Alias o contacto"
                   [value]="recipientRef()" (input)="recipientRef.set(value($event))" />
          </div>
        </div>
        <div class="field">
          <label class="field__label" for="amount">Importe (S/)</label>
          <div class="control">
            <span class="control__icon prefix">S/</span>
            <input id="amount" name="amount" inputmode="decimal" autocomplete="off" placeholder="0.00"
                   [value]="amountText()" (input)="amountText.set(value($event))" />
          </div>
        </div>
        <div class="chips" aria-label="Importes rápidos">
          @for (quick of quickAmounts; track quick) {
            <button type="button" class="chip" (click)="amountText.set(quick.toString())">S/ {{ quick }}</button>
          }
        </div>
        @if (error()) {
          <p class="alert" role="alert" data-testid="payment-error">
            <app-icon name="alert" /><span>{{ error() }}</span>
          </p>
        }
        <button class="btn btn--navy" type="submit" [disabled]="busy()">
          {{ busy() ? 'Validando…' : 'Revisar pago' }}
        </button>
      </form>
    } @else {
      <section data-testid="payment-review" aria-label="Confirmar pago">
        <div class="who">
          <span class="who__avatar" aria-hidden="true"><app-icon name="user" /></span>
          <strong class="who__name" data-testid="review-recipient">
            {{ review_()?.recipient?.displayName }} ({{ review_()?.recipient?.maskedAlias }})
          </strong>
        </div>
        <div class="amount">
          <span class="amount__label">Importe a pagar</span>
          <strong class="amount__value" data-testid="review-amount">{{ amountLabel() }}</strong>
        </div>
        <p class="note"><app-icon name="alert" /><span>Revisa los datos antes de confirmar el pago.</span></p>
        @if (error()) {
          <p class="alert" role="alert" data-testid="payment-error">
            <app-icon name="alert" /><span>{{ error() }}</span>
          </p>
        }
        <div class="stack">
          <button type="button" class="btn" data-testid="confirm" [disabled]="busy()" (click)="confirm()">
            {{ busy() ? 'Procesando…' : 'Confirmar pago' }}
          </button>
          <button type="button" class="btn btn--outline" [disabled]="busy()" (click)="edit()">Volver a editar</button>
        </div>
      </section>
    }
  `,
  styles: `
    .prefix {
      font-weight: 800;
    }
    .who {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 1rem;
      border-radius: 1rem;
      background: var(--brand-background);
      text-align: center;
    }
    .who__avatar {
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: 50%;
      background: #fff;
      color: var(--brand-primary);
    }
    .who__avatar app-icon {
      width: 1.75rem;
      height: 1.75rem;
    }
    .who__name {
      color: var(--brand-primary);
      font-size: 1.05rem;
    }
    .amount {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      margin: 1.25rem 0;
      text-align: center;
    }
    .amount__label {
      font-size: 0.85rem;
      color: var(--brand-muted);
    }
    .amount__value {
      font-size: 2.25rem;
      color: var(--brand-primary);
    }
    .note {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0 0 1.25rem;
      padding: 0.7rem 0.85rem;
      border-radius: 0.75rem;
      background: var(--brand-background);
      color: var(--brand-primary);
      font-size: 0.875rem;
    }
    .note app-icon {
      flex: none;
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
  protected readonly quickAmounts = [10, 20, 50];
  protected readonly amountLabel = computed(() => {
    const result = this.review_();
    return result ? money(result.amount, result.currency) : '';
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
