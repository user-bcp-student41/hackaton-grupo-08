import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from './icon.component';

/**
 * Marco de las pantallas de acceso: cabecera azul con curva, hoja blanca y detalle naranja.
 * En móvil ocupa toda la pantalla; desde 40rem se muestra como una tarjeta centrada de ancho de teléfono.
 * - variant "brand": logo y título centrado (inicio de sesión, éxito).
 * - variant "bar": barra superior con botón de volver y título (pasos del registro).
 */
@Component({
  selector: 'app-auth-shell',
  imports: [RouterLink, IconComponent],
  template: `
    <div class="shell">
      <div class="phone">
        <header class="top" [class.top--bar]="variant() === 'bar'">
          @if (variant() === 'bar') {
            <div class="bar">
              @if (backLink(); as link) {
                <a class="bar__back" [routerLink]="link" aria-label="Volver"><app-icon name="arrow-left" /></a>
              } @else if (backButton()) {
                <button type="button" class="bar__back" aria-label="Volver" (click)="backClick.emit()">
                  <app-icon name="arrow-left" />
                </button>
              } @else {
                <span class="bar__back bar__back--empty"></span>
              }
              <h1 class="bar__title">{{ title() }}</h1>
              <span class="bar__back bar__back--empty"></span>
            </div>
          } @else {
            <p class="logo" aria-hidden="true"><span class="logo__mark">&#8250;&#8250;&#8250;</span>BCP</p>
            @if (title()) {
              <h1 class="brand-title">{{ title() }}</h1>
            }
          }
        </header>
        <main class="sheet">
          <ng-content />
        </main>
        <span class="corner" aria-hidden="true"></span>
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
      overflow: hidden;
      background: var(--brand-surface);
    }

    .top {
      padding: calc(1.5rem + env(safe-area-inset-top)) 1.25rem 3.25rem;
      background: linear-gradient(160deg, var(--brand-primary) 0%, var(--brand-primary-dark) 100%);
      color: var(--brand-primary-contrast);
      text-align: center;
    }

    .top--bar {
      padding-top: calc(0.75rem + env(safe-area-inset-top));
      padding-bottom: 2.5rem;
    }

    .logo {
      margin: 0;
      font-size: 2rem;
      font-weight: 900;
      font-style: italic;
      letter-spacing: -0.02em;
    }

    .logo__mark {
      margin-right: 0.35rem;
      color: var(--brand-accent);
      letter-spacing: -0.15em;
    }

    .brand-title {
      margin: 0.5rem 0 0;
      font-size: 1.25rem;
      font-weight: 700;
    }

    .bar {
      display: grid;
      grid-template-columns: 2.75rem 1fr 2.75rem;
      align-items: center;
    }

    .bar__title {
      margin: 0;
      font-size: 1.0625rem;
      font-weight: 700;
    }

    .bar__back {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.75rem;
      height: 2.75rem;
      padding: 0;
      border: 0;
      border-radius: 50%;
      background: none;
      color: inherit;
      cursor: pointer;
    }

    .bar__back--empty {
      pointer-events: none;
    }

    .sheet {
      position: relative;
      z-index: 1;
      flex: 1;
      margin-top: -1.75rem;
      padding: 1.5rem 1.25rem calc(5.5rem + env(safe-area-inset-bottom));
      border-radius: 1.75rem 1.75rem 0 0;
      background: var(--brand-surface);
    }

    .corner {
      position: absolute;
      right: -3rem;
      bottom: -3rem;
      z-index: 2;
      width: 9rem;
      height: 9rem;
      border-radius: 50%;
      background: var(--brand-accent);
      pointer-events: none;
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

    @media (max-height: 34rem) and (orientation: landscape) {
      .top {
        padding-bottom: 2.5rem;
      }
    }
  `,
})
export class AuthShellComponent {
  readonly title = input('');
  readonly variant = input<'brand' | 'bar'>('brand');
  readonly backLink = input<string | null>(null);
  readonly backButton = input(false);
  readonly backClick = output<void>();
}
