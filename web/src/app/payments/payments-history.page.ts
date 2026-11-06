import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { dayLabel, money, timeLabel } from '../core/format';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';
import { GENERIC_ERROR, PROVISIONAL_NOTICE, STATUS_LABELS } from './payment-labels';
import { PaymentsApi } from './payments.api';
import { HistoryItem, PaymentHistory } from './payments.models';

type Filter = 'ALL' | 'SUCCESS' | 'PENDING' | 'FAILED';

interface DayGroup {
  label: string;
  items: HistoryItem[];
}

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'SUCCESS', label: 'Exitosos' },
  { value: 'PENDING', label: 'En proceso' },
  { value: 'FAILED', label: 'Fallidos' },
];

/** Historial de pagos del pagador (HU2-AC3): estado, importe, destinatario y referencia. */
@Component({
  selector: 'app-payments-history-page',
  imports: [RouterLink, ScreenComponent, IconComponent],
  template: `
    <app-screen title="Mis movimientos" backLink="/home">
      @if (error()) {
        <p class="alert" role="alert"><app-icon name="alert" /><span>{{ error() }}</span></p>
      } @else if (history(); as h) {
        @if (h.items.length === 0) {
          <div class="empty" data-testid="history-empty">
            <app-icon name="receipt" />
            <p>Aún no tienes pagos.</p>
            <a class="btn" routerLink="/payments/new">Hacer mi primer pago</a>
          </div>
        } @else {
          <ul class="tabs" aria-label="Filtrar movimientos">
            @for (f of filters; track f.value) {
              <li>
                <button type="button" class="tab" [attr.aria-pressed]="filter() === f.value" (click)="filter.set(f.value)">
                  {{ f.label }}
                </button>
              </li>
            }
          </ul>
          @for (group of groups(); track group.label) {
            <h2 class="section-title">{{ group.label }}</h2>
            <ul class="list panel" data-testid="history-list">
              @for (item of group.items; track item.paymentId) {
                <li data-testid="history-item">
                  <a class="row" [routerLink]="['/payments', item.paymentId]">
                    <span class="tile" aria-hidden="true"><app-icon name="receipt" /></span>
                    <span class="row__body">
                      <span class="row__title">{{ item.recipient.displayName }}</span>
                      <span class="row__sub">
                        {{ time(item.createdAt) }}
                        @if (item.transactionReference) {
                          <span data-testid="history-reference"> · Ref. {{ item.transactionReference }}</span>
                        }
                      </span>
                    </span>
                    <span class="row__end">
                      {{ amount(item) }}
                      <span class="badge" [class.badge--ok]="item.status === 'SUCCESS'"
                            [class.badge--warn]="item.status === 'PENDING'"
                            [class.badge--bad]="item.status === 'FAILED' || item.status === 'EXPIRED'">
                        {{ labels[item.status] }}
                      </span>
                    </span>
                  </a>
                </li>
              }
            </ul>
          } @empty {
            <p class="empty">No hay movimientos con este filtro.</p>
          }
          <p class="hint">{{ notice }}</p>
        }
      } @else {
        <p class="lead" role="status">Cargando…</p>
      }
    </app-screen>
  `,
})
export class PaymentsHistoryPage implements OnInit {
  private readonly api = inject(PaymentsApi);

  protected readonly labels = STATUS_LABELS;
  protected readonly notice = PROVISIONAL_NOTICE;
  protected readonly filters = FILTERS;
  protected readonly history = signal<PaymentHistory | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<Filter>('ALL');

  protected readonly groups = computed<DayGroup[]>(() => {
    const items = (this.history()?.items ?? []).filter((item) => this.matches(item));
    const byDay = new Map<string, HistoryItem[]>();
    for (const item of items) {
      const label = dayLabel(item.createdAt);
      byDay.set(label, [...(byDay.get(label) ?? []), item]);
    }
    return [...byDay].map(([label, grouped]) => ({ label, items: grouped }));
  });

  ngOnInit(): void {
    this.api.history().subscribe({
      next: (history) => this.history.set(history),
      error: () => this.error.set(GENERIC_ERROR),
    });
  }

  protected amount(item: HistoryItem): string {
    return money(item.amount, item.currency);
  }

  protected time(iso: string): string {
    return timeLabel(iso);
  }

  private matches(item: HistoryItem): boolean {
    switch (this.filter()) {
      case 'ALL':
        return true;
      case 'FAILED':
        return item.status === 'FAILED' || item.status === 'EXPIRED';
      default:
        return item.status === this.filter();
    }
  }
}
