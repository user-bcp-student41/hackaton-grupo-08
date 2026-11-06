import { Routes } from '@angular/router';
import { OnboardingPage } from './onboarding/onboarding.page';
import { PaymentsEntryPage } from './payments/payments-entry.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'onboarding' },
  { path: 'onboarding', component: OnboardingPage },
  { path: 'payments/new', component: PaymentsEntryPage },
  { path: '**', redirectTo: 'onboarding' },
];
