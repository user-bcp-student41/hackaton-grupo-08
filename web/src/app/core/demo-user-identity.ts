import { Injectable } from '@angular/core';

const STORAGE_KEY = 'wallet.demoUserId';

/**
 * Sustituto de pruebas de la identidad (SUPUESTO: el repositorio no tiene autenticación). Genera y conserva un
 * identificador seudónimo por navegador que se envía en el header X-User-Id. No es seguro para producción.
 */
@Injectable({ providedIn: 'root' })
export class DemoUserIdentity {
  private cached: string | null = null;

  userId(): string {
    if (this.cached) {
      return this.cached;
    }
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch {
      stored = null;
    }
    if (!stored) {
      stored = `demo-${this.randomId()}`;
      try {
        localStorage.setItem(STORAGE_KEY, stored);
      } catch {
        // sin almacenamiento: el id vive solo durante la sesión de la pestaña
      }
    }
    this.cached = stored;
    return stored;
  }

  private randomId(): string {
    const cryptoRef = globalThis.crypto;
    if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
      return cryptoRef.randomUUID();
    }
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  }
}
