import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { money } from '../core/format';
import { MOCK_COLLECTION } from '../core/mock-data';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';

/** Mi colecta, vista del padre (DEMO): aportes pagados y pendientes con datos estáticos. */
@Component({
  selector: 'app-collection-page',
  imports: [RouterLink, ScreenComponent, IconComponent],
  template: `
    <app-screen title="Mi colecta" backLink="/home">
      <section class="panel panel--soft head">
        <span class="tile"><app-icon name="users" /></span>
        <div>
          <h2 class="head__name">{{ collection.name }}</h2>
          <p class="head__sub">Tu participación · {{ collection.organizer }}</p>
        </div>
      </section>

      <ul class="list panel" aria-label="Tus aportes">
        <li class="row paid">
          <span class="status status--ok" aria-hidden="true"><app-icon name="check" /></span>
          <span class="row__body"><span class="row__title">Aportes realizados</span></span>
          <strong>{{ paid }}</strong>
        </li>
        <li class="row pending">
          <span class="status status--warn" aria-hidden="true"><app-icon name="clock" /></span>
          <span class="row__body"><span class="row__title">Aporte pendiente</span></span>
          <strong>{{ pending }}</strong>
        </li>
      </ul>

      <div class="stack cta">
        <a class="btn btn--navy" routerLink="/payments/new">Realizar aporte</a>
        <a class="btn btn--outline" routerLink="/payments">Ver historial completo</a>
        <a class="btn-link" routerLink="/collection/movements">Ver movimientos de la colecta</a>
      </div>
    </app-screen>
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }
    .head__name {
      margin: 0;
      font-size: 1.1rem;
      color: var(--brand-primary);
    }
    .head__sub {
      margin: 0;
      font-size: 0.82rem;
      color: var(--brand-muted);
    }
    .pending {
      border-radius: 0.75rem;
      background: #fff1dc;
      border-bottom: 0;
    }
    .status {
      display: grid;
      place-items: center;
      width: 1.75rem;
      height: 1.75rem;
      border-radius: 50%;
    }
    .status--ok {
      background: var(--brand-success-bg);
      color: var(--brand-success);
    }
    .status--warn {
      background: #ffe3b8;
      color: #a85a00;
    }
    .cta {
      margin-top: 1.5rem;
    }
  `,
})
export class CollectionPage {
  protected readonly collection = MOCK_COLLECTION;
  protected readonly paid = money(MOCK_COLLECTION.myPaid);
  protected readonly pending = money(MOCK_COLLECTION.myPending);
}
