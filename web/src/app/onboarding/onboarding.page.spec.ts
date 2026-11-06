import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { userIdInterceptor } from '../core/user-id.interceptor';
import { OnboardingSession } from './onboarding.models';
import { OnboardingPage } from './onboarding.page';

const SESSION_ID = 'b3f1c2a0-5e7d-4c1a-9d3e-0a1b2c3d4e5f';

function session(overrides: Partial<OnboardingSession> = {}): OnboardingSession {
  return {
    sessionId: SESSION_ID,
    status: 'IN_PROGRESS',
    currentStepCode: 'DOCUMENT',
    readyToComplete: false,
    startedAt: '2026-11-06T17:06:11Z',
    completedAt: null,
    steps: [
      { stepCode: 'DOCUMENT', order: 1, status: 'IN_PROGRESS' },
      { stepCode: 'VERIFICATION', order: 2, status: 'PENDING' },
    ],
    ...overrides,
  };
}

describe('OnboardingPage', () => {
  let http: HttpTestingController;

  async function render() {
    const fixture = TestBed.createComponent(OnboardingPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/onboarding/sessions').flush(session());
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function root(fixture: { nativeElement: unknown }): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OnboardingPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([userIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU1-AC1
  it('shows every step in order and marks the current one', async () => {
    const fixture = await render();
    const items = Array.from(root(fixture).querySelectorAll('li.step'));
    expect(items.length).toBe(2);
    expect(items[0].getAttribute('aria-current')).toBe('step');
    expect(items[1].getAttribute('aria-current')).toBeNull();
    expect(root(fixture).textContent).toContain('Paso 1 de 2');
  });

  it('sends the identity header with every API call', async () => {
    const fixture = TestBed.createComponent(OnboardingPage);
    fixture.detectChanges();
    const request = http.expectOne('/api/v1/onboarding/sessions');
    expect(request.request.headers.get('X-User-Id')).toMatch(/^demo-/);
    request.flush(session());
  });

  // HU1-AC2
  it('blocks advancing and shows an actionable message when the API returns 422', async () => {
    const fixture = await render();
    const page = root(fixture);
    const input = page.querySelector('#field-document') as HTMLInputElement;
    input.value = '12345678';
    input.dispatchEvent(new Event('input'));
    const select = page.querySelector('#field-documentType') as HTMLSelectElement;
    select.value = 'TEST';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    (page.querySelector('[data-testid="continue"]') as HTMLButtonElement).click();
    const request = http.expectOne(`/api/v1/onboarding/sessions/${SESSION_ID}/steps/DOCUMENT`);
    expect(request.request.body).toEqual({ data: { documentType: 'TEST', document: '12345678' } });
    request.flush(
      {
        code: 'VALIDATION_ERROR',
        message: 'Revisa los datos marcados para continuar.',
        details: {
          currentStepCode: 'DOCUMENT',
          errors: [
            {
              field: 'document',
              code: 'INVALID_FORMAT',
              message: 'El formato del documento no es válido.',
              howToFix: 'Ingresa el documento de prueba con el formato TEST-xxx.',
            },
          ],
        },
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[data-testid="error-document"]')?.textContent).toContain('TEST-xxx');
    expect((page.querySelector('[data-testid="continue"]') as HTMLButtonElement).disabled).toBe(true);
    expect(page.querySelectorAll('li.step')[0].getAttribute('aria-current')).toBe('step');

    // al corregir el campo se vuelve a habilitar el botón
    input.value = 'TEST-001';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect((page.querySelector('[data-testid="continue"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('keeps the step and lets the user retry when the validator is unavailable (503)', async () => {
    const fixture = await render();
    const page = root(fixture);
    const input = page.querySelector('#field-document') as HTMLInputElement;
    input.value = 'TEST-001';
    input.dispatchEvent(new Event('input'));
    const select = page.querySelector('#field-documentType') as HTMLSelectElement;
    select.value = 'TEST';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    (page.querySelector('[data-testid="continue"]') as HTMLButtonElement).click();
    http
      .expectOne(`/api/v1/onboarding/sessions/${SESSION_ID}/steps/DOCUMENT`)
      .flush(
        {
          code: 'VALIDATION_UNAVAILABLE',
          message: 'No pudimos validar tu información, inténtalo de nuevo.',
          details: {},
        },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[data-testid="banner"]')?.textContent).toContain('No pudimos validar');
    expect((page.querySelector('[data-testid="continue"]') as HTMLButtonElement).disabled).toBe(false);
  });

  // HU1-AC3
  it('finishes the onboarding and navigates to the payments entry point', async () => {
    const fixture = TestBed.createComponent(OnboardingPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne('/api/v1/onboarding/sessions').flush(
      session({
        currentStepCode: null,
        readyToComplete: true,
        steps: [
          { stepCode: 'DOCUMENT', order: 1, status: 'COMPLETED' },
          { stepCode: 'VERIFICATION', order: 2, status: 'COMPLETED' },
        ],
      }),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const finish = root(fixture).querySelector('[data-testid="finish-panel"] button') as HTMLButtonElement;
    finish.click();
    http.expectOne(`/api/v1/onboarding/sessions/${SESSION_ID}/complete`).flush({
      sessionId: SESSION_ID,
      status: 'COMPLETED',
      completedAt: '2026-11-06T17:11:02Z',
      wallet: { walletId: 'w-001', status: 'ACTIVE' },
      paymentsEntryPoint: '/payments/new',
    });

    expect(navigate).toHaveBeenCalledWith('/payments/new');
  });
});
