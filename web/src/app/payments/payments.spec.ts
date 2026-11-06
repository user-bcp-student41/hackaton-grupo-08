import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { userIdInterceptor } from '../core/user-id.interceptor';
import { PaymentFormComponent } from './payment-form.component';
import { PaymentResultPage } from './payment-result.page';
import { Payment } from './payments.models';

function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    paymentId: '11111111-1111-4111-8111-111111111111',
    status: 'PENDING',
    terminal: false,
    retryable: false,
    amount: 50,
    currency: 'PEN',
    recipient: { displayName: 'Contacto de prueba', maskedAlias: '***123' },
    providerId: 'mock-provider-a',
    channel: 'ALIAS',
    transactionReference: null,
    failureCode: null,
    retryOf: null,
    createdAt: '2026-01-01T00:00:00Z',
    terminalAt: null,
    ...overrides,
  };
}

describe('PaymentFormComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([userIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function fill(el: HTMLElement, name: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('shows recipient and amount before confirming and creates no payment until then', async () => {
    const fixture = TestBed.createComponent(PaymentFormComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/checkout-events').flush(null, { status: 202, statusText: 'Accepted' });
    const el = fixture.nativeElement as HTMLElement;

    fill(el, 'recipientRef', 'contacto-123');
    fill(el, 'amount', '50.00');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/payments/validate').flush({
      valid: true,
      recipient: { displayName: 'Contacto de prueba', maskedAlias: '***123' },
      amount: 50,
      currency: 'PEN',
      providerId: 'mock-provider-a',
      channel: 'ALIAS',
    });
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="review-recipient"]')!.textContent).toContain('Contacto de prueba');
    expect(el.querySelector('[data-testid="review-amount"]')!.textContent).toContain('50.00');
    http.expectNone('/api/v1/payments');
  });

  it('rejects an invalid amount on the client without calling the API', () => {
    const fixture = TestBed.createComponent(PaymentFormComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/checkout-events').flush(null);
    const el = fixture.nativeElement as HTMLElement;

    fill(el, 'recipientRef', 'contacto-123');
    fill(el, 'amount', '0');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="payment-error"]')).not.toBeNull();
    http.expectNone('/api/v1/payments/validate');
  });

  it('reuses the idempotency key when the same operation is resubmitted after a network error', () => {
    const fixture = TestBed.createComponent(PaymentFormComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/checkout-events').flush(null);
    const el = fixture.nativeElement as HTMLElement;
    fill(el, 'recipientRef', 'contacto-123');
    fill(el, 'amount', '50');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/payments/validate').flush({
      valid: true,
      recipient: { displayName: 'Contacto de prueba', maskedAlias: '***123' },
      amount: 50,
      currency: 'PEN',
      providerId: 'mock-provider-a',
      channel: 'ALIAS',
    });
    fixture.detectChanges();

    const confirm = el.querySelector<HTMLButtonElement>('[data-testid="confirm"]')!;
    confirm.click();
    const first = http.expectOne('/api/v1/payments');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    first.error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    el.querySelector<HTMLButtonElement>('[data-testid="confirm"]')!.click();
    const second = http.expectOne('/api/v1/payments');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
  });
});

describe('PaymentResultPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([userIdInterceptor])),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => '11111111-1111-4111-8111-111111111111' } } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  const URL = '/api/v1/payments/11111111-1111-4111-8111-111111111111';

  it('shows pending as processing (not terminal), then the single terminal result with reference', () => {
    const fixture = TestBed.createComponent(PaymentResultPage);
    fixture.detectChanges();
    http.expectOne(URL).flush(payment());
    http.expectOne('/api/v1/checkout-events').flush(null);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="payment-pending"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="payment-terminal"]')).toBeNull();
    expect(el.querySelector('[data-testid="payment-reference"]')).toBeNull();
    expect(el.querySelector('[data-testid="retry"]')).toBeNull();

    vi.advanceTimersByTime(1000);
    http.expectOne(URL).flush(
      payment({ status: 'SUCCESS', terminal: true, transactionReference: 'REF-0001', terminalAt: '2026-01-01T00:01:00Z' }),
    );
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="payment-pending"]')).toBeNull();
    expect(el.querySelector('[data-testid="payment-terminal"]')!.getAttribute('data-status')).toBe('SUCCESS');
    expect(el.querySelector('[data-testid="payment-reference"]')!.textContent).toContain('REF-0001');
    vi.advanceTimersByTime(10_000);
    http.expectNone(URL);
  });

  it('offers retry and back on failure, retrying with a fresh key and navigating to the new payment', () => {
    const fixture = TestBed.createComponent(PaymentResultPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    http.expectOne(URL).flush(payment({ status: 'FAILED', terminal: true, retryable: true }));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="back"]')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('[data-testid="retry"]')!.click();
    const retry = http.expectOne(`${URL}/retry`);
    expect(retry.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    retry.flush(payment({ paymentId: '22222222-2222-4222-8222-222222222222', retryOf: '1' }), {
      status: 202,
      statusText: 'Accepted',
    });

    expect(navigate).toHaveBeenCalledWith(['/payments', '22222222-2222-4222-8222-222222222222']);
  });
});
