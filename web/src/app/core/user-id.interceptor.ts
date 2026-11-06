import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { DemoUserIdentity } from './demo-user-identity';

/**
 * Con sesión iniciada envía Authorization: Bearer. Sin sesión conserva el header de pruebas X-User-Id del flujo
 * de onboarding anterior (el servidor solo lo acepta en perfiles de prueba). Ante 401 con token, limpia la sesión.
 */
export const userIdInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const cloned = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request.clone({ setHeaders: { 'X-User-Id': inject(DemoUserIdentity).userId() } });
  return next(cloned).pipe(
    catchError((error: unknown) => {
      if (token && error instanceof HttpErrorResponse && error.status === 401 && !request.url.endsWith('/logout')) {
        auth.clear();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
