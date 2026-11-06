import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CollectionMovementsPage } from './collection/collection-movements.page';
import { HomePage } from './home/home.page';
import { MorePage } from './more/more.page';
import { PaymentFormComponent } from './payments/payment-form.component';
import { PaymentsHistoryPage } from './payments/payments-history.page';

function setup<T>(component: new () => T) {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    imports: [component],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) };
}

describe('HomePage (demo)', () => {
  it('greets the user, shows the balance and lets them hide it', () => {
    const { fixture, root } = setup(HomePage);
    expect(root.querySelector('h1')?.textContent).toContain('Hola, Ana');
    expect(root.querySelector('[data-testid="balance"]')?.textContent).toContain('350.00');
    root.querySelector<HTMLButtonElement>('button[aria-label="Ocultar saldo"]')!.click();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="balance"]')?.textContent).not.toContain('350.00');
  });

  it('links to the real payment screens and renders the bottom navigation', () => {
    const { root } = setup(HomePage);
    expect(root.querySelector('a[href="/payments/new"]')).not.toBeNull();
    expect(root.querySelector('a[href="/payments"]')).not.toBeNull();
    expect(root.querySelectorAll('nav.nav a').length).toBe(4);
  });
});

describe('CollectionMovementsPage (demo)', () => {
  it('filters the static movements by status', () => {
    const { fixture, root } = setup(CollectionMovementsPage);
    const all = root.querySelectorAll('.list .row').length;
    expect(all).toBeGreaterThan(0);
    const pending = [...root.querySelectorAll<HTMLButtonElement>('.tab')].find((b) => b.textContent?.includes('Pendientes'))!;
    pending.click();
    fixture.detectChanges();
    const rows = root.querySelectorAll('.list .row');
    expect(rows.length).toBeLessThan(all);
    rows.forEach((row) => expect(row.textContent).toContain('Pendiente'));
  });
});

describe('MorePage (demo)', () => {
  it('shows the logged document and logs out through the API', () => {
    const { root, http } = setup(MorePage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(root.textContent).toContain('DNI:');
    root.querySelector<HTMLButtonElement>('[data-testid="logout"]')!.click();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});

describe('PaymentFormComponent recipient autocomplete', () => {
  function form() {
    const ctx = setup(PaymentFormComponent);
    ctx.http.expectOne('/api/v1/checkout-events').flush(null);
    const input = ctx.root.querySelector<HTMLInputElement>('input[name="recipientRef"]')!;
    return { ...ctx, input };
  }

  it('offers the 10 demo contacts when the field is focused', () => {
    const { fixture, root, input } = form();
    input.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    const options = root.querySelectorAll('[role="option"]');
    expect(options.length).toBe(10);
    expect(root.textContent).toContain('Valeria Quispe');
    expect(root.textContent).toContain('77777777');
    expect(input.getAttribute('aria-expanded')).toBe('true');
  });

  it('filters by number or by name ignoring accents', () => {
    const { fixture, root, input } = form();
    input.value = 'lucia';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelectorAll('[role="option"]').length).toBe(1);
    expect(root.querySelector('[role="option"]')?.textContent).toContain('Lucía Fernández');

    input.value = '7777';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelector('[role="option"]')?.textContent).toContain('77777777');
  });

  it('fills the number and confirms the contact name when one is picked', () => {
    const { fixture, root, input } = form();
    input.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    root.querySelectorAll<HTMLElement>('[role="option"]')[1].click();
    fixture.detectChanges();
    expect(input.value).toBe('66666666');
    expect(root.querySelector('[data-testid="selected-contact"]')?.textContent).toContain('Diego Ramírez');
    expect(root.querySelector('[role="listbox"]')).toBeNull();
  });

  it('supports keyboard selection and still accepts free text', () => {
    const { fixture, root, input } = form();
    input.dispatchEvent(new Event('focus'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
    fixture.detectChanges();
    expect(input.value).toBe('77777777');

    input.value = 'mock-success';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="selected-contact"]')).toBeNull();
  });
});

describe('PaymentsHistoryPage', () => {
  const item = (id: string, status: string, createdAt: string) => ({
    paymentId: id,
    status,
    amount: 50,
    currency: 'PEN',
    recipient: { displayName: 'Contacto de prueba', maskedAlias: '***123' },
    transactionReference: status === 'SUCCESS' ? 'REF-1' : null,
    createdAt,
    terminalAt: null,
  });

  it('lists payments grouped by day and filters by status', () => {
    const { fixture, root, http } = setup(PaymentsHistoryPage);
    http.expectOne((r) => r.url.startsWith('/api/v1/payments')).flush({
      items: [
        item('1', 'SUCCESS', new Date().toISOString()),
        item('2', 'FAILED', new Date().toISOString()),
        item('3', 'EXPIRED', '2026-01-10T10:00:00Z'),
      ],
      page: 0,
      size: 20,
      totalItems: 3,
    });
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="history-item"]').length).toBe(3);
    expect(root.querySelector('[data-testid="history-reference"]')?.textContent).toContain('REF-1');
    expect(root.querySelectorAll('.section-title').length).toBe(2);

    const failed = [...root.querySelectorAll<HTMLButtonElement>('.tab')].find((b) => b.textContent?.includes('Fallidos'))!;
    failed.click();
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="history-item"]').length).toBe(2);
  });

  it('shows an empty state with a call to action', () => {
    const { fixture, root, http } = setup(PaymentsHistoryPage);
    http.expectOne((r) => r.url.startsWith('/api/v1/payments')).flush({ items: [], page: 0, size: 20, totalItems: 0 });
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="history-empty"]')).not.toBeNull();
    expect(root.querySelector('a[href="/payments/new"]')).not.toBeNull();
  });
});
