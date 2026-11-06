import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type DocumentType = 'DNI' | 'CE';

export interface FieldIssue {
  field: string;
  code: string;
  message: string;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: { errors?: FieldIssue[] };
}

export interface RegistrationView {
  registrationId: string;
  customerStatus: 'UNVALIDATED' | 'VALIDATED';
  currentStep: number | null;
  totalSteps: number;
  steps: { stepCode: string; order: number; status: string }[];
}

export interface LoginView {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  walletStatus: string;
  paymentsEntryPoint: string;
}

const TOKEN_KEY = 'wallet.accessToken';
const DOCUMENT_KEY = 'wallet.documentNumber';

/** Registro, OTP, login y logout. El token vive solo en sessionStorage (se descarta al cerrar la pestaña). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenSignal = signal<string | null>(this.read());

  readonly authenticated = computed(() => this.tokenSignal() !== null);

  token(): string | null {
    return this.tokenSignal();
  }

  register(body: {
    documentType: DocumentType;
    documentNumber: string;
    email: string;
    password: string;
  }): Observable<RegistrationView> {
    return this.http.post<RegistrationView>('/api/v1/registrations', body);
  }

  verifyOtp(registrationId: string, otp: string): Observable<unknown> {
    return this.http.post(`/api/v1/registrations/${registrationId}/otp/verify`, { otp });
  }

  resendOtp(registrationId: string): Observable<unknown> {
    return this.http.post(`/api/v1/registrations/${registrationId}/otp/resend`, {});
  }

  login(body: { documentType: DocumentType; documentNumber: string; password: string }): Observable<LoginView> {
    return this.http.post<LoginView>('/api/v1/auth/login', body).pipe(
      tap((r) => {
        this.store(r.accessToken);
        this.remember(DOCUMENT_KEY, body.documentNumber);
      }),
    );
  }

  /** Documento con el que se inició sesión (solo para mostrarlo en el perfil). */
  documentNumber(): string | null {
    try {
      return sessionStorage.getItem(DOCUMENT_KEY);
    } catch {
      return null;
    }
  }

  /** Invalida la sesión en servidor y descarta el token local (también si el servidor falla). */
  logout(): Observable<unknown> {
    return this.http.post('/api/v1/auth/logout', {}).pipe(tap({ next: () => this.clear(), error: () => this.clear() }));
  }

  clear(): void {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(DOCUMENT_KEY);
    } catch {
      // sin almacenamiento
    }
    this.tokenSignal.set(null);
  }

  private remember(key: string, value: string): void {
    try {
      sessionStorage.setItem(key, value);
    } catch {
      // sin almacenamiento: el dato solo es informativo
    }
  }

  private store(token: string): void {
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // sin almacenamiento: el token vive solo en memoria
    }
    this.tokenSignal.set(token);
  }

  private read(): string | null {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }
}
