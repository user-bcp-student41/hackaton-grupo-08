import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { CollectionMovementsPage } from './collection/collection-movements.page';
import { CollectionPage } from './collection/collection.page';
import { HomePage } from './home/home.page';
import { LoginPage } from './login/login.page';
import { MorePage } from './more/more.page';
import { OnboardingPage } from './onboarding/onboarding.page';
import { PaymentResultPage } from './payments/payment-result.page';
import { PaymentsEntryPage } from './payments/payments-entry.page';
import { PaymentsHistoryPage } from './payments/payments-history.page';
import { RegisterPage } from './registration/register.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'register', component: RegisterPage },
  { path: 'login', component: LoginPage },
  { path: 'onboarding', component: OnboardingPage },
  { path: 'home', component: HomePage, canActivate: [authGuard] },
  { path: 'payments/new', component: PaymentsEntryPage, canActivate: [authGuard] },
  { path: 'payments', pathMatch: 'full', component: PaymentsHistoryPage, canActivate: [authGuard] },
  { path: 'payments/:paymentId', component: PaymentResultPage, canActivate: [authGuard] },
  { path: 'collection', pathMatch: 'full', component: CollectionPage, canActivate: [authGuard] },
  { path: 'collection/movements', component: CollectionMovementsPage, canActivate: [authGuard] },
  { path: 'more', component: MorePage, canActivate: [authGuard] },
  { path: '**', redirectTo: 'home' },
];
