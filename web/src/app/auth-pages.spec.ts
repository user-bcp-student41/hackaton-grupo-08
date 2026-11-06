import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginPage } from './login/login.page';
import { RegisterPage } from './registration/register.page';

function setup<T>(component: new () => T) {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    imports: [component],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return { fixture, root, http: TestBed.inject(HttpTestingController) };
}

function type(root: HTMLElement, selector: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('LoginPage', () => {
  it('shows the document and password fields with a way to create an account', () => {
    const { root } = setup(LoginPage);
    expect(root.querySelector('#docNumber')).not.toBeNull();
    expect(root.querySelector('#password')).not.toBeNull();
    expect(root.querySelector('a[href="/register"]')).not.toBeNull();
  });

  it('blocks the submit and explains what to fix when the data is invalid', async () => {
    const { fixture, root, http } = setup(LoginPage);
    type(root, '#docNumber', '1234');
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('#docNumber-error')?.textContent).toContain('8 dígitos');
    expect(root.querySelector('#password-error')).not.toBeNull();
    expect(root.querySelector('#docNumber')?.getAttribute('aria-invalid')).toBe('true');
    http.expectNone('/api/v1/auth/login');
  });

  it('toggles the password visibility with an accessible control', async () => {
    const { fixture, root } = setup(LoginPage);
    const toggle = root.querySelector<HTMLButtonElement>('.control__action')!;
    expect(root.querySelector('#password')?.getAttribute('type')).toBe('password');
    expect(toggle.getAttribute('aria-label')).toBe('Mostrar contraseña');
    toggle.click();
    fixture.detectChanges();
    expect(root.querySelector('#password')?.getAttribute('type')).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
  });
});

describe('RegisterPage', () => {
  it('starts on step 1 of 2 with a progress bar', () => {
    const { root } = setup(RegisterPage);
    expect(root.querySelector('[data-testid="step-indicator"]')?.textContent).toContain('Paso 1 de 2');
    expect(root.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('1');
  });

  it('keeps the user on step 1 and flags every invalid field', async () => {
    const { fixture, root, http } = setup(RegisterPage);
    type(root, '#email', 'correo-invalido');
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="step-indicator"]')?.textContent).toContain('Paso 1 de 2');
    expect(root.querySelector('#docNumber-error')).not.toBeNull();
    expect(root.querySelector('#email-error')).not.toBeNull();
    expect(root.querySelector('#password-error')).not.toBeNull();
    http.expectNone('/api/v1/registrations');
  });

  it('moves to the code step after registering and lets the user go back to fix the data', async () => {
    const { fixture, root, http } = setup(RegisterPage);
    type(root, '#docNumber', '12345678');
    type(root, '#email', 'ana@example.com');
    type(root, '#password', '123456');
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/registrations').flush({
      registrationId: 'r-1',
      customerStatus: 'UNVALIDATED',
      currentStep: 2,
      totalSteps: 2,
      steps: [],
    });
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="step-indicator"]')?.textContent).toContain('Paso 2 de 2');
    expect(root.querySelector('#otp')).not.toBeNull();
    expect(root.textContent).toContain('ana@example.com');

    root.querySelector<HTMLButtonElement>('button[aria-label="Volver"]')!.click();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="step-indicator"]')?.textContent).toContain('Paso 1 de 2');
  });

  it('shows the active-wallet confirmation after validating the code', () => {
    const { fixture, root, http } = setup(RegisterPage);
    type(root, '#docNumber', '12345678');
    type(root, '#email', 'ana@example.com');
    type(root, '#password', '123456');
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/registrations').flush({ registrationId: 'r-1' });
    fixture.detectChanges();
    type(root, '#otp', '654321');
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/registrations/r-1/otp/verify').flush({});
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="registration-done"]')?.textContent).toContain('Tu billetera está activa');
  });
});
