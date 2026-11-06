import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { DemoUserIdentity } from './demo-user-identity';

/** Agrega el identificador de usuario de pruebas a las llamadas a la API propia. */
export const userIdInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const userId = inject(DemoUserIdentity).userId();
  return next(request.clone({ setHeaders: { 'X-User-Id': userId } }));
};
