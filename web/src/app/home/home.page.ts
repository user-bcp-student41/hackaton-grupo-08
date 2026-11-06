import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { money } from '../core/format';
import { MOCK_BALANCE, MOCK_COLLECTION, MOCK_USER } from '../core/mock-data';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';

/**
 * Inicio (DEMO): saludo, saldo y resumen de colecta con datos estáticos de `mock-data.ts`.
 * Los accesos a pagos y movimientos sí llevan a pantallas reales.
 */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, ScreenComponent, IconComponent],
  template: `
    <app-screen variant="home" [title]="'Hola, ' + user.firstName">
      <section class="panel panel--soft balance" aria-label="Saldo disponible">
        <div>
          <p class="balance__label">Saldo disponible</p>
          <p class="balance__value" data-testid="balance">{{ showBalance() ? balance : 'S/ ••••••' }}</p>
        </div>
        <button type="button" class="control__action" (click)="showBalance.set(!showBalance())"
                [attr.aria-label]="showBalance() ? 'Ocultar saldo' : 'Mostrar saldo'"
                [attr.aria-pressed]="!showBalance()">
          <app-icon [name]="showBalance() ? 'eye' : 'eye-off'" />
        </button>
      </section>

      <nav class="actions" aria-label="Accesos rápidos">
        <a class="action" routerLink="/payments/new">
          <span class="tile"><app-icon name="send" /></span>
          <span>Pagar a contacto</span>
        </a>
        <a class="action" routerLink="/payments">
          <span class="tile"><app-icon name="list" /></span>
          <span>Mis movimientos</span>
        </a>
        <a class="action" routerLink="/collection">
          <span class="tile"><app-icon name="users" /></span>
          <span>Mi colecta</span>
        </a>
      </nav>

      <section class="panel" aria-label="Colecta">
        <div class="collection__head">
          <span class="tile"><app-icon name="users" /></span>
          <h2 class="collection__name">Colecta {{ collection.name }}</h2>
        </div>
        <div class="progress__track" role="progressbar" aria-label="Avance de la colecta" aria-valuemin="0"
             aria-valuemax="100" [attr.aria-valuenow]="percent">
          <div class="progress__bar" [style.width.%]="percent"></div>
        </div>
        <div class="collection__nums">
          <span>Recaudado<strong>{{ raised }}</strong></span>
          <span>Meta<strong>{{ goal }}</strong></span>
        </div>
        <a class="btn-link" routerLink="/collection/movements">Ver movimientos de la colecta</a>
      </section>
    </app-screen>
  `,
  styles: `
    .balance {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
    }
    .balance__label {
      margin: 0;
      font-size: 0.85rem;
      color: var(--brand-muted);
    }
    .balance__value {
      margin: 0.1rem 0 0;
      font-size: 1.9rem;
      font-weight: 800;
      color: var(--brand-primary);
    }
    .actions {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.6rem;
      margin-bottom: 1rem;
    }
    .action {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 0.85rem 0.4rem;
      border: 1px solid var(--brand-border);
      border-radius: 1rem;
      color: var(--brand-text);
      font-size: 0.78rem;
      font-weight: 600;
      text-align: center;
      text-decoration: none;
    }
    .collection__head {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.9rem;
    }
    .collection__name {
      margin: 0;
      font-size: 1rem;
      color: var(--brand-primary);
    }
    .collection__nums {
      display: flex;
      justify-content: space-between;
      margin: 0.6rem 0 0.25rem;
      font-size: 0.8rem;
      color: var(--brand-muted);
    }
    .collection__nums span {
      display: flex;
      flex-direction: column;
    }
    .collection__nums span:last-child {
      align-items: flex-end;
    }
    .collection__nums strong {
      font-size: 1rem;
      color: var(--brand-text);
    }
  `,
})
export class HomePage {
  protected readonly user = MOCK_USER;
  protected readonly collection = MOCK_COLLECTION;
  protected readonly balance = money(MOCK_BALANCE);
  protected readonly raised = money(MOCK_COLLECTION.raised);
  protected readonly goal = money(MOCK_COLLECTION.goal);
  protected readonly percent = Math.round((MOCK_COLLECTION.raised / MOCK_COLLECTION.goal) * 100);
  protected readonly showBalance = signal(true);
}
