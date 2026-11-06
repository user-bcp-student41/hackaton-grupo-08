import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { LoginPage } from './login/login.page';
import { PaymentsEntryPage } from './payments/payments-entry.page';
import { RegisterPage } from './registration/register.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'register' },
  { path: 'register', component: RegisterPage },
  { path: 'login', component: LoginPage },
  { path: 'payments/new', component: PaymentsEntryPage, canActivate: [authGuard] },
  { path: '**', redirectTo: 'register' },
import { OnboardingPage } from './onboarding/onboarding.page';
import { PaymentResultPage } from './payments/payment-result.page';
import { PaymentsEntryPage } from './payments/payments-entry.page';
import { PaymentsHistoryPage } from './payments/payments-history.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'onboarding' },
  { path: 'onboarding', component: OnboardingPage },
  { path: 'payments/new', component: PaymentsEntryPage },
  { path: 'payments', pathMatch: 'full', component: PaymentsHistoryPage },
  { path: 'payments/:paymentId', component: PaymentResultPage },
  { path: '**', redirectTo: 'onboarding' },
];
