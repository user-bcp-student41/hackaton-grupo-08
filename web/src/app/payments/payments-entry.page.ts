import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OnboardingApi } from '../onboarding/onboarding.api';

type AccessState = 'checking' | 'allowed' | 'blocked' | 'error';

/**
 * Marcador del punto de entrada de pagos de bajo monto. El flujo de pago pertenece a HU-02: esta pantalla solo
 * confirma, consultando la API, que la wallet está ACTIVE y dirige al onboarding si no lo está.
 */
@Component({
  selector: 'app-payments-entry-page',
  imports: [RouterLink],
  template: `
    <main class="payments">
      <h1>Pagos de bajo monto</h1>
      @switch (state()) {
        @case ('checking') {
          <p role="status">Verificando el estado de tu wallet…</p>
        }
        @case ('allowed') {
          <p data-testid="payments-enabled">
            Tu wallet está activa. El flujo de pago se habilita con HU-02.
          </p>
        }
        @case ('blocked') {
          <p data-testid="payments-blocked">
            Tu wallet aún no está activa. Completa tu registro para pagar.
            <a routerLink="/register">Ir al registro</a>
          </p>
        }
        @default {
          <p role="alert">No pudimos verificar tu wallet. Inténtalo de nuevo en unos minutos.</p>
        }
      }
    </main>
  `,
  styles: `
    .payments {
      max-width: 40rem;
      margin: 0 auto;
      padding: 1.5rem 1rem;
      font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
    }
  `,
})
export class PaymentsEntryPage implements OnInit {
  private readonly api = inject(OnboardingApi);
  protected readonly state = signal<AccessState>('checking');

  ngOnInit(): void {
    this.api.wallet().subscribe({
      next: (wallet) => this.state.set(wallet.status === 'ACTIVE' ? 'allowed' : 'blocked'),
      error: (error: { status?: number }) => this.state.set(error.status === 404 ? 'blocked' : 'error'),
    });
  }
}
