import { Component, computed, signal } from '@angular/core';
import { money } from '../core/format';
import { CollectionMovement, MOCK_COLLECTION, MOCK_COLLECTION_MOVEMENTS } from '../core/mock-data';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';

type Filter = 'ALL' | 'CONFIRMED' | 'PENDING';

/** Movimientos de la colecta, vista del delegado (DEMO): datos estáticos con filtros funcionales. */
@Component({
  selector: 'app-collection-movements-page',
  imports: [ScreenComponent, IconComponent],
  template: `
    <app-screen title="Movimientos de la colecta" backLink="/collection">
      <h2 class="heading">{{ collection.name }}</h2>
      <div class="totals">
        <span>Recaudado<strong>{{ raised }}</strong></span>
        <span>Meta<strong>{{ goal }}</strong></span>
      </div>
      <div class="progress__track" role="progressbar" aria-label="Avance de la colecta" aria-valuemin="0"
           aria-valuemax="100" [attr.aria-valuenow]="percent">
        <div class="progress__bar" [style.width.%]="percent"></div>
      </div>
      <p class="percent">{{ percent }}%</p>

      <ul class="tabs" aria-label="Filtrar aportes">
        @for (tab of tabs(); track tab.value) {
          <li>
            <button type="button" class="tab" [attr.aria-pressed]="filter() === tab.value" (click)="filter.set(tab.value)">
              {{ tab.label }} ({{ tab.count }})
            </button>
          </li>
        }
      </ul>

      <ul class="list panel">
        @for (item of visible(); track item.name) {
          <li>
            <div class="row">
              <span class="tile tile--blue" aria-hidden="true"><app-icon name="user" /></span>
              <span class="row__body"><span class="row__title">{{ item.name }}</span></span>
              <span class="row__end">
                {{ amount(item) }}
                <span class="badge" [class.badge--ok]="item.status === 'CONFIRMED'"
                      [class.badge--warn]="item.status === 'PENDING'">
                  {{ item.status === 'CONFIRMED' ? 'Confirmado' : 'Pendiente' }}
                </span>
              </span>
            </div>
          </li>
        }
      </ul>
    </app-screen>
  `,
  styles: `
    .totals {
      display: flex;
      justify-content: space-between;
      margin-bottom: 0.5rem;
      font-size: 0.8rem;
      color: var(--brand-muted);
    }
    .totals span {
      display: flex;
      flex-direction: column;
    }
    .totals span:last-child {
      align-items: flex-end;
    }
    .totals strong {
      font-size: 1.1rem;
      color: var(--brand-text);
    }
    .percent {
      margin: 0.35rem 0 1.25rem;
      text-align: right;
      font-size: 0.8rem;
      font-weight: 700;
      color: var(--brand-primary);
    }
  `,
})
export class CollectionMovementsPage {
  protected readonly collection = MOCK_COLLECTION;
  protected readonly raised = money(MOCK_COLLECTION.raised);
  protected readonly goal = money(MOCK_COLLECTION.goal);
  protected readonly percent = Math.round((MOCK_COLLECTION.raised / MOCK_COLLECTION.goal) * 100);
  protected readonly filter = signal<Filter>('ALL');

  private readonly movements = MOCK_COLLECTION_MOVEMENTS;
  protected readonly tabs = computed(() => [
    { value: 'ALL' as Filter, label: 'Todos', count: this.movements.length },
    { value: 'CONFIRMED' as Filter, label: 'Confirmados', count: this.count('CONFIRMED') },
    { value: 'PENDING' as Filter, label: 'Pendientes', count: this.count('PENDING') },
  ]);
  protected readonly visible = computed(() =>
    this.filter() === 'ALL' ? this.movements : this.movements.filter((m) => m.status === this.filter()),
  );

  protected amount(item: CollectionMovement): string {
    return money(item.amount);
  }

  private count(status: CollectionMovement['status']): number {
    return this.movements.filter((m) => m.status === status).length;
  }
}
