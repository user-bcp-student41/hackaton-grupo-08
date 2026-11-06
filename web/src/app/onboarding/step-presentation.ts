/**
 * Presentación de cada paso del onboarding (título y campos del formulario). La API no entrega este catálogo
 * (solo stepCode/order/status), por lo que la interfaz lo define aquí; la validez de los datos SIEMPRE la decide
 * la API. Los pasos y campos reflejan el contrato del mock KYC y deben ajustarse si Producto define otros.
 */
export interface FieldOption {
  value: string;
  label: string;
}

export interface FieldDefinition {
  name: string;
  label: string;
  hint: string;
  type: 'text' | 'select';
  required: boolean;
  initialValue: string;
  options?: FieldOption[];
}

export interface StepPresentation {
  title: string;
  description: string;
  fields: FieldDefinition[];
}

export const STEP_PRESENTATION: Record<string, StepPresentation> = {
  DOCUMENT: {
    title: 'Documento',
    description: 'Ingresa tu documento de prueba para verificar tu identidad.',
    fields: [
      {
        name: 'documentType',
        label: 'Tipo de documento',
        hint: 'Solo el entorno de prueba admite el tipo TEST.',
        type: 'select',
        required: true,
        initialValue: '',
        options: [{ value: 'TEST', label: 'TEST (prueba)' }],
      },
      {
        name: 'document',
        label: 'Número de documento',
        hint: 'Debe empezar con TEST- (por ejemplo, TEST-001). No uses documentos reales.',
        type: 'text',
        required: true,
        initialValue: '',
      },
    ],
  },
  VERIFICATION: {
    title: 'Verificación',
    description: 'Confirma la verificación simulada de tu identidad.',
    fields: [
      {
        name: 'scenario',
        label: 'Resultado de la verificación simulada',
        hint: 'Entorno de prueba: elige APPROVED para continuar.',
        type: 'select',
        required: false,
        initialValue: 'APPROVED',
        options: [
          { value: 'APPROVED', label: 'APPROVED (aprobada)' },
          { value: 'REJECTED', label: 'REJECTED (rechazada)' },
          { value: 'MANUAL_REVIEW', label: 'MANUAL_REVIEW (revisión manual)' },
          { value: 'UNAVAILABLE', label: 'UNAVAILABLE (proveedor no disponible)' },
        ],
      },
    ],
  },
};

export function presentationFor(stepCode: string): StepPresentation {
  return (
    STEP_PRESENTATION[stepCode] ?? {
      title: stepCode,
      description: 'Completa este paso para continuar.',
      fields: [],
    }
  );
}
