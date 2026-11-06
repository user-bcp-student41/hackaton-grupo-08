import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiErrorBody, AuthService, DocumentType } from '../core/auth.service';
import { DOCUMENT_LENGTH, PASSWORD_LENGTH, digitsOnly, documentError, passwordError } from '../core/digits';
import { AuthShellComponent } from '../core/ui/auth-shell.component';
import { IconComponent } from '../core/ui/icon.component';

/** Inicio de sesión con documento (DNI o CE) y contraseña enmascarada. */
@Component({
  selector: 'app-login-page',
  imports: [RouterLink, AuthShellComponent, IconComponent],
  template: `
    <app-auth-shell title="Mi Billetera">
      <form (submit)="submit($event)" novalidate>
        <div class="field">
          <label class="field__label" for="docType">Tipo de documento</label>
          <div class="control">
            <select id="docType" [value]="documentType()" (change)="documentType.set(asType($event))">
              <option value="DNI">DNI</option>
              <option value="CE">CE</option>
            </select>
            <span class="control__icon"><app-icon name="chevron-down" /></span>
          </div>
        </div>
        <div class="field">
          <label class="field__label" for="docNumber">Número de documento</label>
          <div class="control" [class.control--error]="errors()['documentNumber']">
            <span class="control__icon"><app-icon name="user" /></span>
            <input id="docNumber" type="text" inputmode="numeric" autocomplete="username" placeholder="12345678"
                   [attr.maxlength]="docLen" [value]="documentNumber()"
                   [attr.aria-invalid]="errors()['documentNumber'] ? 'true' : null"
                   [attr.aria-describedby]="errors()['documentNumber'] ? 'docNumber-error' : null"
                   (input)="onDigits($event, documentNumber, docLen)" />
          </div>
          @if (errors()['documentNumber']) {
            <p class="field-error" id="docNumber-error" role="alert">
              <app-icon name="alert" /><span>{{ errors()['documentNumber'] }}</span>
            </p>
          }
        </div>
        <div class="field">
          <label class="field__label" for="password">Contraseña de 6 dígitos</label>
          <div class="control" [class.control--error]="errors()['password']">
            <span class="control__icon"><app-icon name="lock" /></span>
            <input id="password" [type]="showPassword() ? 'text' : 'password'" inputmode="numeric"
                   autocomplete="current-password" [attr.maxlength]="pwdLen" [value]="password()"
                   [attr.aria-invalid]="errors()['password'] ? 'true' : null"
                   [attr.aria-describedby]="errors()['password'] ? 'password-error' : null"
                   (input)="onDigits($event, password, pwdLen)" />
            <button type="button" class="control__action" (click)="showPassword.set(!showPassword())"
                    [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                    [attr.aria-pressed]="showPassword()">
              <app-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
            </button>
          </div>
          @if (errors()['password']) {
            <p class="field-error" id="password-error" role="alert">
              <app-icon name="alert" /><span>{{ errors()['password'] }}</span>
            </p>
          }
        </div>
        @if (formError()) {
          <p role="alert" class="alert"><app-icon name="alert" /><span>{{ formError() }}</span></p>
        }
        <div class="stack">
          <button class="btn" type="submit" [disabled]="busy()">{{ busy() ? 'Ingresando…' : 'Ingresar' }}</button>
          <a class="btn btn--outline" routerLink="/register">Crear cuenta</a>
        </div>
      </form>
    </app-auth-shell>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly docLen = DOCUMENT_LENGTH;
  protected readonly pwdLen = PASSWORD_LENGTH;
  protected readonly documentType = signal<DocumentType>('DNI');
  protected readonly documentNumber = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly formError = signal('');
  protected readonly busy = signal(false);

  protected asType(e: Event): DocumentType {
    return (e.target as HTMLSelectElement).value === 'CE' ? 'CE' : 'DNI';
  }

  protected onDigits(e: Event, target: { set(v: string): void }, max: number): void {
    const input = e.target as HTMLInputElement;
    const clean = digitsOnly(input.value, max);
    input.value = clean;
    target.set(clean);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const errors: Record<string, string> = {};
    const doc = documentError(this.documentNumber());
    if (doc) errors['documentNumber'] = doc;
    const pwd = passwordError(this.password());
    if (pwd) errors['password'] = pwd;
    this.errors.set(errors);
    this.formError.set('');
    if (Object.keys(errors).length > 0) return;
    this.busy.set(true);
    this.auth
      .login({ documentType: this.documentType(), documentNumber: this.documentNumber(), password: this.password() })
      .subscribe({
        next: () => {
          this.password.set('');
          this.busy.set(false);
          void this.router.navigateByUrl('/home');
        },
        error: (e: HttpErrorResponse) => {
          this.busy.set(false);
          const body = (e.error ?? {}) as ApiErrorBody;
          this.formError.set(body.message ?? 'No pudimos iniciar tu sesión. Inténtalo de nuevo.');
        },
      });
  }
}
