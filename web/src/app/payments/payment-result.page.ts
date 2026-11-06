import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { dateTimeLabel, money } from '../core/format';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';
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
  imports: [RouterLink, ScreenComponent, IconComponent],
  template: `
    <app-screen title="Resultado del pago" [showNav]="false">
      @if (payment(); as p) {
        @if (!p.terminal) {
          <section class="state" data-testid="payment-pending" role="status">
            <span class="spinner" aria-hidden="true"></span>
            <h2 class="heading">Estamos procesando tu pago</h2>
            <p class="lead">Esto puede tomar unos segundos. Te informaremos el resultado.</p>
            <span class="badge badge--warn"><app-icon name="clock" />{{ labels.PENDING }}</span>
          </section>
          @if (gaveUp()) {
            <p class="note" data-testid="payment-still-processing">
              Sigue procesando. Puedes salir: consulta el resultado en tus movimientos.
            </p>
          }
        } @else {
          <section class="state state--{{ p.status }}" data-testid="payment-terminal" [attr.data-status]="p.status"
                   role="status">
            <span class="state__icon" aria-hidden="true">
              <app-icon [name]="p.status === 'SUCCESS' ? 'check' : 'x'" />
            </span>
            <h2 class="heading">{{ headline(p) }}</h2>
            <p class="lead">{{ message(p) }}</p>
            <span class="badge" [class.badge--ok]="p.status === 'SUCCESS'"
                  [class.badge--bad]="p.status !== 'SUCCESS'">{{ labels[p.status] }}</span>
          </section>
        }

        <dl class="panel details">
          <div class="kv"><dt>Destinatario</dt><dd>{{ p.recipient.displayName }} ({{ p.recipient.maskedAlias }})</dd></div>
          @if (p.status === 'SUCCESS' && p.transactionReference) {
            <div class="kv" data-testid="payment-reference"><dt>Referencia</dt><dd>{{ p.transactionReference }}</dd></div>
          }
          <div class="kv"><dt>Monto</dt><dd>{{ amount(p) }}</dd></div>
          <div class="kv"><dt>Fecha y hora</dt><dd>{{ when(p) }}</dd></div>
        </dl>

        @if (error()) {
          <p class="alert" role="alert" data-testid="payment-error"><app-icon name="alert" /><span>{{ error() }}</span></p>
        }
        <div class="stack">
          @if (p.retryable) {
            <button type="button" class="btn" data-testid="retry" [disabled]="busy()" (click)="retry(p)">
              {{ busy() ? 'Reintentando…' : 'Reintentar' }}
            </button>
          }
          <a class="btn btn--navy" routerLink="/payments" data-testid="to-history">Ver mis movimientos</a>
          @if (p.terminal) {
            <a class="btn btn--outline" routerLink="/payments/new" data-testid="back">Hacer otro pago</a>
          }
        </div>
        <p class="hint footnote">{{ notice }}</p>
      } @else if (error()) {
        <p class="alert" role="alert" data-testid="payment-error"><app-icon name="alert" /><span>{{ error() }}</span></p>
        <a class="btn btn--outline" routerLink="/payments/new">Volver</a>
      } @else {
        <p class="lead" role="status">Cargando…</p>
      }
    </app-screen>
  `,
  styles: `
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.4rem;
      margin-bottom: 1.25rem;
      padding: 1.5rem 1rem;
      border-radius: 1rem;
      text-align: center;
    }
    .state .lead {
      margin-bottom: 0.5rem;
    }
    .state--SUCCESS {
      background: var(--brand-success-bg);
    }
    .state--FAILED,
    .state--EXPIRED {
      background: var(--brand-error-bg);
    }
    .state__icon {
      display: grid;
      place-items: center;
      width: 4rem;
      height: 4rem;
      border-radius: 50%;
      color: #fff;
    }
    .state__icon app-icon {
      width: 2rem;
      height: 2rem;
    }
    .state--SUCCESS .state__icon {
      background: var(--brand-success);
    }
    .state--FAILED .state__icon,
    .state--EXPIRED .state__icon {
      background: var(--brand-error);
    }
    .state--SUCCESS .heading {
      color: var(--brand-success);
    }
    .state--FAILED .heading,
    .state--EXPIRED .heading {
      color: var(--brand-error);
    }
    .spinner {
      width: 5rem;
      height: 5rem;
      margin-bottom: 0.75rem;
      border: 0.45rem solid #dce5fa;
      border-top-color: var(--brand-accent);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner {
        animation: none;
      }
    }
    .details {
      margin: 0 0 1.25rem;
    }
    .note {
      margin: 0 0 1rem;
      padding: 0.7rem 0.85rem;
      border-radius: 0.75rem;
      background: var(--brand-background);
      color: var(--brand-primary);
      font-size: 0.875rem;
    }
    .footnote {
      margin: 1rem 0 0;
      text-align: center;
    }
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

  protected headline(payment: Payment): string {
    switch (payment.status) {
      case 'SUCCESS':
        return 'Pago realizado';
      case 'EXPIRED':
        return 'El pago expiró';
      default:
        return 'No se pudo realizar el pago';
    }
  }

  protected message(payment: Payment): string {
    switch (payment.status) {
      case 'SUCCESS':
        return 'Tu pago se registró correctamente.';
      case 'EXPIRED':
        return 'No recibimos la confirmación a tiempo. No se realizó ningún cobro.';
      default:
        return 'No pudimos completar tu pago. Puedes intentarlo nuevamente.';
    }
  }

  protected amount(payment: Payment): string {
    return money(payment.amount, payment.currency);
  }

  protected when(payment: Payment): string {
    return dateTimeLabel(payment.terminalAt ?? payment.createdAt);
  }

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
