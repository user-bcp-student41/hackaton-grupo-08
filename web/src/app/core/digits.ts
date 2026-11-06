/** Deja solo dígitos y recorta a `max` (cubre escribir y pegar). */
export function digitsOnly(value: string, max: number): string {
  return value.replace(/\D/g, '').slice(0, max);
}

export const DOCUMENT_LENGTH = 8;
export const PASSWORD_LENGTH = 6;
export const OTP_LENGTH = 6;
export const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;

export function documentError(value: string): string | null {
  if (!value) return 'Ingresa tu número de documento de 8 dígitos.';
  if (!/^\d{8}$/.test(value)) return `El documento debe tener exactamente 8 dígitos (te faltan ${8 - value.length}).`;
  return null;
}

export function passwordError(value: string): string | null {
  if (!value) return 'Ingresa una contraseña de 6 dígitos.';
  if (!/^\d{6}$/.test(value)) return 'La contraseña debe tener exactamente 6 dígitos.';
  return null;
}
