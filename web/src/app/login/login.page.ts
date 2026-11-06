import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiErrorBody, AuthService, DocumentType } from '../core/auth.service';
import { DOCUMENT_LENGTH, PASSWORD_LENGTH, digitsOnly, documentError, passwordError } from '../core/digits';

/** Inicio de sesión con documento (DNI o CE) y contraseña enmascarada. */
@Component({
  selector: 'app-login-page',
  imports: [RouterLink],
  template: `
    <main class="card">
      <h1>Inicia sesión</h1>
      <form (submit)="submit($event)" novalidate>
        <div class="field">
          <label for="docType">Tipo de documento</label>
          <select id="docType" (change)="documentType.set(asType($event))">
            <option value="DNI">DNI</option>
            <option value="CE">CE</option>
          </select>
        </div>
        <div class="field">
          <label for="docNumber">Número de documento</label>
          <input id="docNumber" type="text" inputmode="numeric" autocomplete="username" [attr.maxlength]="docLen"
                 [value]="documentNumber()" (input)="onDigits($event, documentNumber, docLen)" />
          @if (errors()['documentNumber']) { <span class="field-error">{{ errors()['documentNumber'] }}</span> }
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input id="password" [type]="showPassword() ? 'text' : 'password'" inputmode="numeric"
                 autocomplete="current-password" [attr.maxlength]="pwdLen" [value]="password()"
                 (input)="onDigits($event, password, pwdLen)" />
          <button type="button" class="btn-link" (click)="showPassword.set(!showPassword())">
            {{ showPassword() ? 'Ocultar' : 'Mostrar' }} contraseña
          </button>
          @if (errors()['password']) { <span class="field-error">{{ errors()['password'] }}</span> }
        </div>
        @if (formError()) { <p role="alert" class="field-error">{{ formError() }}</p> }
        <button class="btn" type="submit" [disabled]="busy()">Ingresar</button>
      </form>
      <p>¿Aún no tienes cuenta? <a routerLink="/register">Regístrate</a></p>
    </main>
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
        next: (r) => {
          this.password.set('');
          this.busy.set(false);
          void this.router.navigateByUrl(r.paymentsEntryPoint);
        },
        error: (e: HttpErrorResponse) => {
          this.busy.set(false);
          const body = (e.error ?? {}) as ApiErrorBody;
          this.formError.set(body.message ?? 'No pudimos iniciar tu sesión. Inténtalo de nuevo.');
        },
      });
  }
}
