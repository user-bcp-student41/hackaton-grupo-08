import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiErrorBody, AuthService, DocumentType } from '../core/auth.service';
import { DOCUMENT_LENGTH, EMAIL_PATTERN, OTP_LENGTH, PASSWORD_LENGTH, digitsOnly, documentError, passwordError } from '../core/digits';
import { AuthShellComponent } from '../core/ui/auth-shell.component';
import { IconComponent } from '../core/ui/icon.component';

/** Registro en 2 pasos (HU-01): datos y código OTP enviado al correo; termina con la billetera activa. */
@Component({
  selector: 'app-register-page',
  imports: [RouterLink, AuthShellComponent, IconComponent],
  template: `
    @if (step() === 3) {
      <app-auth-shell>
        <section class="done" data-testid="registration-done">
          <span class="done__badge" aria-hidden="true">
            <app-icon name="check" />
            <i class="done__spark done__spark--a"></i>
            <i class="done__spark done__spark--b"></i>
          </span>
          <h1 class="heading">Tu billetera está activa</h1>
          <p class="lead" role="status">
            ¡Listo! Validamos tu correo. Inicia sesión para realizar pagos y transferencias de forma rápida y segura.
          </p>
          <a class="btn" routerLink="/login">Iniciar sesión</a>
        </section>
      </app-auth-shell>
    } @else {
      <app-auth-shell variant="bar" title="Activar mi billetera" [backLink]="step() === 1 ? '/login' : null"
                      [backButton]="step() === 2" (backClick)="backToData()">
        <div class="progress">
          <p class="progress__caption" data-testid="step-indicator" role="status">Paso {{ step() }} de 2</p>
          <div class="progress__track" role="progressbar" aria-label="Avance del registro" aria-valuemin="0"
               aria-valuemax="2" [attr.aria-valuenow]="step()">
            <div class="progress__bar" [style.width.%]="step() * 50"></div>
          </div>
        </div>

        @if (step() === 1) {
          <h2 class="heading">Completa tus datos</h2>
          <p class="lead">Ingresa la información solicitada para continuar.</p>
          <form (submit)="submitData($event)" novalidate>
            <div class="field">
              <label class="field__label" for="docType">Tipo de documento</label>
              <div class="control" [class.control--error]="err('documentType')">
                <select id="docType" [value]="documentType()" (change)="documentType.set(asType($event))">
                  <option value="DNI">DNI</option>
                  <option value="CE">CE</option>
                </select>
                <span class="control__icon"><app-icon name="chevron-down" /></span>
              </div>
              @if (err('documentType')) {
                <p class="field-error" role="alert"><app-icon name="alert" /><span>{{ err('documentType') }}</span></p>
              }
            </div>
            <div class="field">
              <label class="field__label" for="docNumber">Número de documento</label>
              <div class="control" [class.control--error]="err('documentNumber')">
                <span class="control__icon"><app-icon name="user" /></span>
                <input id="docNumber" type="text" inputmode="numeric" autocomplete="off" placeholder="12345678"
                       [attr.maxlength]="docLen" [value]="documentNumber()"
                       [attr.aria-invalid]="err('documentNumber') ? 'true' : null"
                       [attr.aria-describedby]="err('documentNumber') ? 'docNumber-error' : null"
                       (input)="onDocument($event)" />
              </div>
              @if (err('documentNumber')) {
                <p class="field-error" id="docNumber-error" role="alert">
                  <app-icon name="alert" /><span>{{ err('documentNumber') }}</span>
                </p>
              }
            </div>
            <div class="field">
              <label class="field__label" for="email">Correo electrónico</label>
              <div class="control" [class.control--error]="err('email')">
                <span class="control__icon"><app-icon name="mail" /></span>
                <input id="email" type="email" inputmode="email" autocomplete="email" placeholder="nombre@correo.com"
                       [value]="email()" [attr.aria-invalid]="err('email') ? 'true' : null"
                       [attr.aria-describedby]="err('email') ? 'email-error' : null"
                       (input)="email.set(val($event))" />
              </div>
              @if (err('email')) {
                <p class="field-error" id="email-error" role="alert">
                  <app-icon name="alert" /><span>{{ err('email') }}</span>
                </p>
              }
            </div>
            <div class="field">
              <label class="field__label" for="password">Contraseña de 6 dígitos</label>
              <div class="control" [class.control--error]="err('password')">
                <span class="control__icon"><app-icon name="lock" /></span>
                <input id="password" [type]="showPassword() ? 'text' : 'password'" inputmode="numeric"
                       autocomplete="new-password" [attr.maxlength]="pwdLen" [value]="password()"
                       [attr.aria-invalid]="err('password') ? 'true' : null"
                       [attr.aria-describedby]="err('password') ? 'password-error' : null"
                       (input)="onPassword($event)" />
                <button type="button" class="control__action" (click)="showPassword.set(!showPassword())"
                        [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                        [attr.aria-pressed]="showPassword()">
                  <app-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
                </button>
              </div>
              @if (err('password')) {
                <p class="field-error" id="password-error" role="alert">
                  <app-icon name="alert" /><span>{{ err('password') }}</span>
                </p>
              }
            </div>
            @if (formError()) {
              <p role="alert" class="alert"><app-icon name="alert" /><span>{{ formError() }}</span></p>
            }
            <button class="btn btn--navy" type="submit" [disabled]="busy()">
              {{ busy() ? 'Validando…' : 'Continuar' }}
            </button>
          </form>
          <p class="link-row">¿Ya tienes cuenta? <a routerLink="/login">Inicia sesión</a></p>
        } @else {
          <h2 class="heading">Verifica tu correo</h2>
          <p class="lead">
            Enviamos un código de {{ otpLen }} dígitos a <strong class="email">{{ email() }}</strong>. Ingrésalo para
            activar tu billetera.
          </p>
          <form (submit)="submitOtp($event)" novalidate>
            <div class="field">
              <label class="field__label" for="otp">Código de verificación</label>
              <div class="control control--otp" [class.control--error]="formError()">
                <input id="otp" type="text" inputmode="numeric" autocomplete="one-time-code" placeholder="••••••"
                       [attr.maxlength]="otpLen" [value]="otp()" (input)="otp.set(digits($event, otpLen))" />
              </div>
            </div>
            <p class="hint">¿Te equivocaste en algún dato? Vuelve atrás para corregirlo.</p>
            @if (formError()) {
              <p role="alert" class="alert"><app-icon name="alert" /><span>{{ formError() }}</span></p>
            }
            @if (info()) {
              <p role="status" class="alert alert--success"><app-icon name="check" /><span>{{ info() }}</span></p>
            }
            <div class="stack">
              <button class="btn btn--navy" type="submit" [disabled]="busy()">
                {{ busy() ? 'Validando…' : 'Validar código' }}
              </button>
              <button class="btn-link" type="button" [disabled]="busy()" (click)="resend()">Reenviar código</button>
            </div>
          </form>
        }
      </app-auth-shell>
    }
  `,
  styles: `
    .email {
      overflow-wrap: anywhere;
    }
    .done {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      text-align: center;
    }
    .done .btn {
      margin-top: 0.75rem;
    }
    .done__badge {
      position: relative;
      display: grid;
      place-items: center;
      width: 6.5rem;
      height: 6.5rem;
      margin: -4.5rem 0 1rem;
      border: 0.4rem solid #fff;
      border-radius: 50%;
      background: var(--brand-success);
      color: #fff;
      box-shadow: 0 6px 18px rgb(30 142 78 / 35%);
    }
    .done__badge app-icon {
      width: 3.25rem;
      height: 3.25rem;
    }
    .done__spark {
      position: absolute;
      width: 0.3rem;
      height: 1.1rem;
      border-radius: 999px;
      background: var(--brand-accent);
    }
    .done__spark--a {
      top: -0.6rem;
      right: 0.2rem;
      transform: rotate(30deg);
    }
    .done__spark--b {
      top: 0.2rem;
      right: -0.9rem;
      transform: rotate(75deg);
    }
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);

  protected readonly docLen = DOCUMENT_LENGTH;
  protected readonly pwdLen = PASSWORD_LENGTH;
  protected readonly otpLen = OTP_LENGTH;

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

  /** Vuelve a los datos para corregirlos; al reenviar se genera un registro y un código nuevos. */
  protected backToData(): void {
    this.otp.set('');
    this.errors.set({});
    this.formError.set('');
    this.info.set('');
    this.step.set(1);
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
