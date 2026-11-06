import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OnboardingApi } from '../onboarding/onboarding.api';
import { IconComponent } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';
import { PaymentFormComponent } from './payment-form.component';

type AccessState = 'checking' | 'allowed' | 'blocked' | 'error';

/**
 * Punto de entrada de pagos de bajo monto. Confirma, consultando la API, que la wallet está ACTIVE antes de
 * mostrar el formulario; si no lo está, dirige al registro.
 */
@Component({
  selector: 'app-payments-entry-page',
  imports: [RouterLink, PaymentFormComponent, ScreenComponent, IconComponent],
  template: `
    <app-screen title="Pagar" backLink="/home" [showNav]="false">
      @switch (state()) {
        @case ('checking') {
          <p class="lead" role="status">Verificando el estado de tu billetera…</p>
        }
        @case ('allowed') {
          <app-payment-form />
          <p class="link-row"><a routerLink="/payments">Ver mis movimientos</a></p>
        }
        @case ('blocked') {
          <div class="panel panel--soft" data-testid="payments-blocked">
            <h2 class="heading">Activa tu billetera</h2>
            <p class="lead">Tu billetera aún no está activa. Completa tu registro para pagar.</p>
            <a class="btn" routerLink="/register">Ir al registro</a>
          </div>
        }
        @default {
          <p class="alert" role="alert">
            <app-icon name="alert" />
            <span>No pudimos verificar tu billetera. Inténtalo de nuevo en unos minutos.</span>
          </p>
        }
      }
    </app-screen>
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
