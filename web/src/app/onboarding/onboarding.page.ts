import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OnboardingApi } from './onboarding.api';
import { ApiError, FieldError, OnboardingSession } from './onboarding.models';
import { FieldDefinition, StepPresentation, presentationFor } from './step-presentation';

@Component({
  selector: 'app-onboarding-page',
  templateUrl: './onboarding.page.html',
  styleUrl: './onboarding.page.css',
})
export class OnboardingPage implements OnInit {
  private readonly api = inject(OnboardingApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly session = signal<OnboardingSession | null>(null);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly values = signal<Record<string, string>>({});
  protected readonly fieldErrors = signal<Record<string, FieldError>>({});
  protected readonly generalErrors = signal<FieldError[]>([]);
  protected readonly banner = signal<string | null>(null);

  protected readonly currentStepCode = computed(() => this.session()?.currentStepCode ?? null);
  protected readonly presentation = computed<StepPresentation | null>(() => {
    const code = this.currentStepCode();
    return code ? presentationFor(code) : null;
  });
  protected readonly totalSteps = computed(() => this.session()?.steps.length ?? 0);
  protected readonly currentPosition = computed(() => {
    const current = this.session()?.steps.find((step) => step.status === 'IN_PROGRESS');
    return current ? current.order : this.totalSteps();
  });
  protected readonly readyToComplete = computed(() => this.session()?.readyToComplete ?? false);
  /** Mientras haya errores de validación sin corregir, no se permite continuar (HU1-AC2). */
  protected readonly hasBlockingErrors = computed(
    () => Object.keys(this.fieldErrors()).length > 0 || this.generalErrors().length > 0,
  );
  protected readonly canContinue = computed(() => !this.submitting() && !this.hasBlockingErrors());

  ngOnInit(): void {
    this.startOrResume();
  }

  protected stepTitle(stepCode: string): string {
    return presentationFor(stepCode).title;
  }

  protected valueOf(field: FieldDefinition): string {
    return this.values()[field.name] ?? field.initialValue;
  }

  protected errorFor(field: FieldDefinition): FieldError | null {
    return this.fieldErrors()[field.name] ?? null;
  }

  protected onInput(field: FieldDefinition, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement;
    this.values.update((current) => ({ ...current, [field.name]: target.value }));
    if (this.fieldErrors()[field.name]) {
      this.fieldErrors.update((current) => {
        const { [field.name]: _removed, ...rest } = current;
        return rest;
      });
    }
    if (this.generalErrors().length > 0) {
      this.generalErrors.set([]);
    }
    this.banner.set(null);
  }

  protected submitStep(): void {
    const session = this.session();
    const stepCode = this.currentStepCode();
    const presentation = this.presentation();
    if (!session || !stepCode || !presentation || !this.canContinue()) {
      return;
    }
    const data: Record<string, string> = {};
    for (const field of presentation.fields) {
      const value = this.valueOf(field);
      if (value !== '') {
        data[field.name] = value;
      }
    }
    this.submitting.set(true);
    this.banner.set(null);
    this.api.submitStep(session.sessionId, stepCode, data).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.fieldErrors.set({});
        this.generalErrors.set([]);
        this.values.set({});
        this.session.set(updated);
      },
      error: (error: HttpErrorResponse) => this.handleStepError(error, session.sessionId),
    });
  }

  protected finish(): void {
    const session = this.session();
    if (!session || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.banner.set(null);
    this.api.complete(session.sessionId).subscribe({
      next: (result) => {
        this.submitting.set(false);
        void this.router.navigateByUrl(result.paymentsEntryPoint);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = this.asApiError(error);
        if (body?.code === 'STEPS_INCOMPLETE') {
          this.banner.set('Aún faltan pasos por completar. Revisa el paso marcado como actual.');
          this.refresh(session.sessionId);
        } else {
          this.banner.set(this.genericMessage(error));
        }
      },
    });
  }

  private startOrResume(): void {
    const source = this.route.snapshot.queryParamMap.get('source');
    this.api.start(source).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (this.asApiError(error)?.code === 'ONBOARDING_ALREADY_COMPLETED') {
          void this.router.navigateByUrl('/payments/new');
          return;
        }
        this.banner.set(this.genericMessage(error));
      },
    });
  }

  private handleStepError(error: HttpErrorResponse, sessionId: string): void {
    this.submitting.set(false);
    const body = this.asApiError(error);
    if (error.status === 422 && body?.details?.errors) {
      const known = new Set(
        (this.presentation()?.fields ?? []).map((field) => field.name),
      );
      const byField: Record<string, FieldError> = {};
      const general: FieldError[] = [];
      for (const item of body.details.errors) {
        if (known.has(item.field) && !byField[item.field]) {
          byField[item.field] = item;
        } else {
          general.push(item);
        }
      }
      this.fieldErrors.set(byField);
      this.generalErrors.set(general);
      this.banner.set(body.message);
      return;
    }
    if (error.status === 409 && body?.code === 'STEP_OUT_OF_ORDER') {
      this.banner.set(body.message);
      this.refresh(sessionId);
      return;
    }
    this.banner.set(this.genericMessage(error));
  }

  private refresh(sessionId: string): void {
    this.api.getSession(sessionId).subscribe({
      next: (session) => this.session.set(session),
      error: () => undefined,
    });
  }

  private genericMessage(error: HttpErrorResponse): string {
    const body = this.asApiError(error);
    if (error.status === 503 && body?.code === 'VALIDATION_UNAVAILABLE') {
      return body.message;
    }
    if (error.status >= 400 && error.status < 500 && body?.message) {
      return body.message;
    }
    return 'No pudimos completar la operación. Inténtalo de nuevo en unos minutos.';
  }

  private asApiError(error: HttpErrorResponse): ApiError | null {
    const body: unknown = error.error;
    if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
      return body as ApiError;
    }
    return null;
  }
}
