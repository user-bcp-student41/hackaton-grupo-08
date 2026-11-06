import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CompleteResponse, OnboardingSession, WalletInfo } from './onboarding.models';

const BASE = '/api/v1';

@Injectable({ providedIn: 'root' })
export class OnboardingApi {
  private readonly http = inject(HttpClient);

  /** Inicia el onboarding o reanuda la sesión en curso (201 / 200). */
  start(acquisitionSource: string | null): Observable<OnboardingSession> {
    const body = acquisitionSource ? { acquisitionSource } : {};
    return this.http.post<OnboardingSession>(`${BASE}/onboarding/sessions`, body);
  }

  submitStep(
    sessionId: string,
    stepCode: string,
    data: Record<string, string>,
  ): Observable<OnboardingSession> {
    return this.http.put<OnboardingSession>(
      `${BASE}/onboarding/sessions/${encodeURIComponent(sessionId)}/steps/${encodeURIComponent(stepCode)}`,
      { data },
    );
  }

  getSession(sessionId: string): Observable<OnboardingSession> {
    return this.http.get<OnboardingSession>(
      `${BASE}/onboarding/sessions/${encodeURIComponent(sessionId)}`,
    );
  }

  complete(sessionId: string): Observable<CompleteResponse> {
    return this.http.post<CompleteResponse>(
      `${BASE}/onboarding/sessions/${encodeURIComponent(sessionId)}/complete`,
      null,
    );
  }

  wallet(): Observable<WalletInfo> {
    return this.http.get<WalletInfo>(`${BASE}/wallets/me`);
  }
}
