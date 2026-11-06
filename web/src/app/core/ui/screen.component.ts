import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent, IconName } from './icon.component';

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
}

const NAV: NavItem[] = [
  { path: '/home', label: 'Inicio', icon: 'home' },
  { path: '/payments/new', label: 'Pagar', icon: 'send' },
  { path: '/payments', label: 'Movimientos', icon: 'list' },
  { path: '/more', label: 'Más', icon: 'grid' },
];

/**
 * Marco de las pantallas con sesión: cabecera azul, hoja blanca y barra de navegación inferior opcional.
 * - variant "home": logo, campana y saludo (title).
 * - variant "bar": botón de volver (backLink) y título centrado.
 * En móvil ocupa toda la pantalla; desde 40rem se muestra como tarjeta centrada de ancho de teléfono.
 */
@Component({
  selector: 'app-screen',
  imports: [RouterLink, RouterLinkActive, IconComponent],
  template: `
    <div class="shell">
      <div class="phone">
        <header class="top" [class.top--home]="variant() === 'home'">
          @if (variant() === 'home') {
            <div class="home-bar">
              <p class="logo" aria-hidden="true"><span class="logo__mark">&#8250;&#8250;&#8250;</span>BCP</p>
              <span class="bell" aria-hidden="true"><app-icon name="bell" /></span>
            </div>
            <h1 class="home-title">{{ title() }}</h1>
          } @else {
            <div class="bar">
              @if (backLink(); as link) {
                <a class="bar__side" [routerLink]="link" aria-label="Volver"><app-icon name="arrow-left" /></a>
              } @else {
                <span class="bar__side"></span>
              }
              <h1 class="bar__title">{{ title() }}</h1>
              <span class="bar__side"></span>
            </div>
          }
        </header>
        <main class="sheet" [class.sheet--nav]="showNav()">
          <ng-content />
        </main>
        @if (showNav()) {
          <nav class="nav" aria-label="Navegación principal">
            @for (item of items; track item.path) {
              <a class="nav__item" [routerLink]="item.path" routerLinkActive="nav__item--active"
                 [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page">
                <app-icon [name]="item.icon" />
                <span>{{ item.label }}</span>
              </a>
            }
          </nav>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .shell {
      display: flex;
      justify-content: center;
      min-height: 100vh;
      min-height: 100dvh;
    }

    .phone {
      position: relative;
      display: flex;
      flex-direction: column;
      width: 100%;
      min-height: 100vh;
      min-height: 100dvh;
      overflow: clip;
      background: var(--brand-surface);
    }

    .top {
      padding: calc(0.75rem + env(safe-area-inset-top)) 1.25rem 2.75rem;
      background: linear-gradient(160deg, var(--brand-primary) 0%, var(--brand-primary-dark) 100%);
      color: var(--brand-primary-contrast);
    }

    .top--home {
      padding-top: calc(1rem + env(safe-area-inset-top));
      padding-bottom: 3.25rem;
    }

    .home-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .logo {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 900;
      font-style: italic;
      letter-spacing: -0.02em;
    }

    .logo__mark {
      margin-right: 0.3rem;
      color: var(--brand-accent);
      letter-spacing: -0.15em;
    }

    .bell {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.5rem;
      height: 2.5rem;
    }

    .home-title {
      margin: 0.75rem 0 0;
      font-size: 1.375rem;
      font-weight: 800;
    }

    .bar {
      display: grid;
      grid-template-columns: 2.75rem 1fr 2.75rem;
      align-items: center;
    }

    .bar__title {
      margin: 0;
      text-align: center;
      font-size: 1.0625rem;
      font-weight: 700;
    }

    .bar__side {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.75rem;
      height: 2.75rem;
      color: inherit;
    }

    .sheet {
      position: relative;
      z-index: 1;
      flex: 1;
      margin-top: -1.75rem;
      padding: 1.25rem 1.25rem calc(1.5rem + env(safe-area-inset-bottom));
      border-radius: 1.75rem 1.75rem 0 0;
      background: var(--brand-surface);
    }

    .sheet--nav {
      padding-bottom: 1.5rem;
    }

    .nav {
      position: sticky;
      bottom: 0;
      z-index: 5;
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      padding: 0.35rem 0.25rem calc(0.35rem + env(safe-area-inset-bottom));
      border-top: 1px solid var(--brand-border);
      background: var(--brand-surface);
    }

    .nav__item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.15rem;
      min-height: 3.25rem;
      justify-content: center;
      border-radius: 0.75rem;
      color: var(--brand-muted);
      font-size: 0.75rem;
      font-weight: 600;
      text-decoration: none;
    }

    .nav__item--active {
      color: var(--brand-accent);
    }

    @media (min-width: 40rem) {
      .shell {
        align-items: center;
        padding: 1.5rem;
      }

      .phone {
        width: 26rem;
        min-height: min(52rem, calc(100dvh - 3rem));
        border-radius: 1.75rem;
        box-shadow: 0 12px 40px rgb(11 42 138 / 18%);
      }
    }
  `,
})
export class ScreenComponent {
  readonly title = input('');
  readonly variant = input<'home' | 'bar'>('bar');
  readonly backLink = input<string | null>(null);
  readonly showNav = input(true);
  protected readonly items = NAV;
}
