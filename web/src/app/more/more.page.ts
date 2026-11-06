import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { MOCK_USER } from '../core/mock-data';
import { IconComponent, IconName } from '../core/ui/icon.component';
import { ScreenComponent } from '../core/ui/screen.component';

interface MenuItem {
  icon: IconName;
  label: string;
  hint?: string;
}

/**
 * Más (DEMO): perfil y opciones. Los ítems del menú son informativos hasta que existan esos módulos;
 * "Cerrar sesión" sí invalida la sesión en el servidor.
 */
@Component({
  selector: 'app-more-page',
  imports: [ScreenComponent, IconComponent],
  template: `
    <app-screen title="Más" backLink="/home">
      <section class="profile">
        <span class="profile__avatar" aria-hidden="true"><app-icon name="user" /></span>
        <div>
          <h2 class="profile__name">{{ user.fullName }}</h2>
          <p class="profile__doc">DNI: {{ documentNumber }}</p>
        </div>
      </section>

      <ul class="list panel">
        @for (item of items; track item.label) {
          <li>
            <div class="row">
              <span class="tile tile--blue" aria-hidden="true"><app-icon [name]="item.icon" /></span>
              <span class="row__body">
                <span class="row__title">{{ item.label }}</span>
                @if (item.hint) {
                  <span class="row__sub">{{ item.hint }}</span>
                }
              </span>
              <span class="soon">Próximamente</span>
            </div>
          </li>
        }
      </ul>

      <button type="button" class="btn logout" data-testid="logout" [disabled]="busy()" (click)="logout()">
        <app-icon name="log-out" />Cerrar sesión
      </button>
    </app-screen>
  `,
  styles: `
    .profile {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      margin-bottom: 1.25rem;
    }
    .profile__avatar {
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: 50%;
      background: #e3ebff;
      color: var(--brand-primary);
    }
    .profile__avatar app-icon {
      width: 1.75rem;
      height: 1.75rem;
    }
    .profile__name {
      margin: 0;
      font-size: 1.15rem;
      color: var(--brand-primary);
    }
    .profile__doc {
      margin: 0;
      font-size: 0.85rem;
      color: var(--brand-muted);
    }
    .soon {
      font-size: 0.72rem;
      font-weight: 600;
      color: var(--brand-muted);
    }
    .logout {
      margin-top: 1.25rem;
      border-color: var(--brand-accent);
      background: #fff4ec;
      color: var(--brand-accent-dark);
    }
    .logout:hover:not([disabled]) {
      background: #ffe6d4;
    }
  `,
})
export class MorePage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = MOCK_USER;
  protected readonly documentNumber = this.auth.documentNumber() ?? MOCK_USER.documentNumber;
  protected readonly busy = signal(false);
  protected readonly items: MenuItem[] = [
    { icon: 'user', label: 'Mi perfil' },
    { icon: 'shield', label: 'Seguridad', hint: 'Contraseña y acceso' },
    { icon: 'help', label: 'Ayuda' },
    { icon: 'file', label: 'Términos y condiciones' },
  ];

  protected logout(): void {
    this.busy.set(true);
    const done = () => void this.router.navigateByUrl('/login');
    this.auth.logout().subscribe({ next: done, error: done });
  }
}
