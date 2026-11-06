import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GENERIC_ERROR, PROVISIONAL_NOTICE, STATUS_LABELS } from './payment-labels';
import { PaymentsApi } from './payments.api';
import { PaymentHistory } from './payments.models';

/** Historial de pagos del pagador (HU2-AC3): estado, importe, destinatario y referencia. */
@Component({
  selector: 'app-payments-history-page',
  imports: [RouterLink, DatePipe],
  template: `
    <main class="payments">
      <h1>Historial de pagos</h1>
      <p><small>{{ notice }}</small></p>
      @if (error()) {
        <p role="alert">{{ error() }}</p>
      } @else if (history(); as h) {
        @if (h.items.length === 0) {
          <p data-testid="history-empty">Aún no tienes pagos.</p>
        } @else {
          <ul data-testid="history-list">
            @for (item of h.items; track item.paymentId) {
              <li data-testid="history-item">
                <a [routerLink]="['/payments', item.paymentId]">
                  {{ item.recipient.displayName }} · {{ item.currency }} {{ item.amount.toFixed(2) }}
                </a>
                — <strong>{{ labels[item.status] }}</strong>
                @if (item.transactionReference) {
                  <span data-testid="history-reference"> · Ref. {{ item.transactionReference }}</span>
                }
                <small> · {{ item.createdAt | date: 'short' }}</small>
              </li>
            }
          </ul>
        }
      } @else {
        <p role="status">Cargando…</p>
      }
      <a routerLink="/payments/new">Nuevo pago</a>
    </main>
  `,
})
export class PaymentsHistoryPage implements OnInit {
  private readonly api = inject(PaymentsApi);

  protected readonly labels = STATUS_LABELS;
  protected readonly notice = PROVISIONAL_NOTICE;
  protected readonly history = signal<PaymentHistory | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.history().subscribe({
      next: (history) => this.history.set(history),
      error: () => this.error.set(GENERIC_ERROR),
    });
  }
}
