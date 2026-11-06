export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED';
export type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

export interface OnboardingStep {
  stepCode: string;
  order: number;
  status: StepStatus;
}

export interface OnboardingSession {
  sessionId: string;
  status: SessionStatus;
  currentStepCode: string | null;
  readyToComplete: boolean;
  startedAt: string;
  completedAt: string | null;
  steps: OnboardingStep[];
}

export interface WalletInfo {
  walletId: string;
  status: string;
}

export interface CompleteResponse {
  sessionId: string;
  status: SessionStatus;
  completedAt: string;
  wallet: WalletInfo;
  paymentsEntryPoint: string;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
  howToFix: string;
}

export interface ApiError {
  code: string;
  message: string;
  details?: {
    currentStepCode?: string;
    errors?: FieldError[];
    pendingSteps?: string[];
  };
}
