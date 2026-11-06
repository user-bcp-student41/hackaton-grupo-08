import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiErrorBody, AuthService, DocumentType } from '../core/auth.service';
import { DOCUMENT_LENGTH, EMAIL_PATTERN, OTP_LENGTH, PASSWORD_LENGTH, digitsOnly, documentError, passwordError } from '../core/digits';

/** Registro en 2 pasos (HU-01): datos y código OTP enviado al correo. */
@Component({
  selector: 'app-register-page',
  imports: [RouterLink],
  template: `
    <main class="card">
      <h1>Crea tu cuenta</h1>
      <ol class="steps" aria-label="Pasos del registro">
        @for (item of stepLabels; track item.n) {
          <li [class.active]="step() === item.n" [attr.aria-current]="step() === item.n ? 'step' : null">
            {{ item.n }}. {{ item.label }}
          </li>
        }
      </ol>
      <p data-testid="step-indicator" role="status">Paso {{ step() }} de 2</p>

      @if (step() === 1) {
        <form (submit)="submitData($event)" novalidate>
          <div class="field">
            <label for="docType">Tipo de documento</label>
            <select id="docType" [value]="documentType()" (change)="documentType.set(asType($event))">
              <option value="DNI">DNI</option>
              <option value="CE">CE</option>
            </select>
            @if (err('documentType')) { <span class="field-error">{{ err('documentType') }}</span> }
          </div>
          <div class="field">
            <label for="docNumber">Número de documento</label>
            <input id="docNumber" type="text" inputmode="numeric" autocomplete="off"
                   [attr.maxlength]="docLen" [value]="documentNumber()" (input)="onDocument($event)" />
            @if (err('documentNumber')) { <span class="field-error">{{ err('documentNumber') }}</span> }
          </div>
          <div class="field">
            <label for="email">Correo electrónico</label>
            <input id="email" type="email" autocomplete="email" [value]="email()" (input)="email.set(val($event))" />
            @if (err('email')) { <span class="field-error">{{ err('email') }}</span> }
          </div>
          <div class="field">
            <label for="password">Contraseña de 6 dígitos</label>
            <input id="password" [type]="showPassword() ? 'text' : 'password'" inputmode="numeric"
                   autocomplete="new-password" [attr.maxlength]="pwdLen" [value]="password()"
                   (input)="onPassword($event)" />
            <button type="button" class="btn-link" (click)="showPassword.set(!showPassword())">
              {{ showPassword() ? 'Ocultar' : 'Mostrar' }} contraseña
            </button>
            @if (err('password')) { <span class="field-error">{{ err('password') }}</span> }
          </div>
          @if (formError()) { <p role="alert" class="field-error">{{ formError() }}</p> }
          <button class="btn" type="submit" [disabled]="busy()">Continuar</button>
        </form>
        <p>¿Ya tienes cuenta? <a routerLink="/login">Inicia sesión</a></p>
      } @else if (step() === 2) {
        <form (submit)="submitOtp($event)" novalidate>
          <p>Enviamos un código a tu correo. Ingrésalo para activar tu wallet.</p>
          <p class="hint">Si necesitas corregir tus datos, inicia un nuevo registro.</p>
          <div class="field">
            <label for="otp">Código de verificación</label>
            <input id="otp" type="text" inputmode="numeric" autocomplete="one-time-code"
                   [attr.maxlength]="otpLen" [value]="otp()" (input)="otp.set(digits($event, otpLen))" />
          </div>
          @if (formError()) { <p role="alert" class="field-error">{{ formError() }}</p> }
          @if (info()) { <p role="status">{{ info() }}</p> }
          <button class="btn" type="submit" [disabled]="busy()">Validar código</button>
          <button class="btn-link" type="button" [disabled]="busy()" (click)="resend()">Reenviar código</button>
        </form>
      } @else {
        <section data-testid="registration-done">
          <p role="status">¡Listo! Validamos tu correo y tu wallet está activa.</p>
          <a class="btn" routerLink="/login">Iniciar sesión</a>
        </section>
      }
    </main>
  `,
  styles: `
    .steps { display: flex; gap: 1rem; list-style: none; padding: 0; color: #6b7280; }
    .steps .active { color: var(--brand-accent); font-weight: 700; }
    .hint { font-size: 0.875rem; color: #6b7280; }
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);

  protected readonly docLen = DOCUMENT_LENGTH;
  protected readonly pwdLen = PASSWORD_LENGTH;
  protected readonly otpLen = OTP_LENGTH;
  protected readonly stepLabels = [
    { n: 1, label: 'Tus datos' },
    { n: 2, label: 'Código al correo' },
  ];

  protected readonly step = signal<1 | 2 | 3>(1);
  protected readonly documentType = signal<DocumentType>('DNI');
  protected readonly documentNumber = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly otp = signal('');
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly formError = signal('');
  protected readonly info = signal('');
  protected readonly busy = signal(false);
  private registrationId = '';

  protected err(field: string): string | undefined {
    return this.errors()[field];
  }
  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }
  protected asType(e: Event): DocumentType {
    return this.val(e) === 'CE' ? 'CE' : 'DNI';
  }
  protected digits(e: Event, max: number): string {
    const input = e.target as HTMLInputElement;
    const clean = digitsOnly(input.value, max);
    input.value = clean;
    return clean;
  }
  protected onDocument(e: Event): void {
    this.documentNumber.set(this.digits(e, DOCUMENT_LENGTH));
  }
  protected onPassword(e: Event): void {
    this.password.set(this.digits(e, PASSWORD_LENGTH));
  }

  protected submitData(event: Event): void {
    event.preventDefault();
    const errors: Record<string, string> = {};
    const doc = documentError(this.documentNumber());
    if (doc) errors['documentNumber'] = doc;
    if (!this.email()) errors['email'] = 'Ingresa tu correo electrónico.';
    else if (!EMAIL_PATTERN.test(this.email())) errors['email'] = 'Ingresa un correo válido, por ejemplo nombre@dominio.com.';
    const pwd = passwordError(this.password());
    if (pwd) errors['password'] = pwd;
    this.errors.set(errors);
    this.formError.set('');
    if (Object.keys(errors).length > 0) {
      this.formError.set('Corrige los campos marcados para continuar.');
      return;
    }
    this.busy.set(true);
    this.auth
      .register({
        documentType: this.documentType(),
        documentNumber: this.documentNumber(),
        email: this.email(),
        password: this.password(),
      })
      .subscribe({
        next: (r) => {
          this.registrationId = r.registrationId;
          this.password.set('');
          this.busy.set(false);
          this.step.set(2);
        },
        error: (e: HttpErrorResponse) => this.fail(e),
      });
  }

  protected submitOtp(event: Event): void {
    event.preventDefault();
    this.formError.set('');
    this.info.set('');
    if (this.otp().length !== OTP_LENGTH) {
      this.formError.set(`Ingresa el código de ${OTP_LENGTH} dígitos que enviamos a tu correo.`);
      return;
    }
    this.busy.set(true);
    this.auth.verifyOtp(this.registrationId, this.otp()).subscribe({
      next: () => {
        this.busy.set(false);
        this.step.set(3);
      },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  protected resend(): void {
    this.formError.set('');
    this.info.set('');
    this.busy.set(true);
    this.auth.resendOtp(this.registrationId).subscribe({
      next: () => {
        this.busy.set(false);
        this.otp.set('');
        this.info.set('Te enviamos un código nuevo. El anterior ya no sirve.');
      },
      error: (e: HttpErrorResponse) => this.fail(e),
    });
  }

  private fail(e: HttpErrorResponse): void {
    this.busy.set(false);
    const body = (e.error ?? {}) as ApiErrorBody;
    const fieldErrors = body.details?.errors;
    if (e.status === 422 && fieldErrors?.length) {
      const map: Record<string, string> = {};
      for (const issue of fieldErrors) map[issue.field] = issue.message;
      this.errors.set(map);
      this.formError.set('Corrige los campos marcados para continuar.');
      return;
    }
    this.formError.set(body.message ?? 'No pudimos completar la solicitud. Inténtalo de nuevo.');
  }
}
