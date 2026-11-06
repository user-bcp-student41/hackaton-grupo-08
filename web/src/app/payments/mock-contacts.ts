/**
 * Contactos de demostración que el mock del proveedor reconoce por número (mismos datos que
 * `InProcessMockPaymentProvider.DEMO_CONTACTS` en la API). Sirven solo para autocompletar el destinatario;
 * el nombre que se muestra al confirmar siempre lo devuelve la API.
 */
export interface MockContact {
  ref: string;
  name: string;
}

export const MOCK_CONTACTS: MockContact[] = [
  { ref: '77777777', name: 'Valeria Quispe' },
  { ref: '66666666', name: 'Diego Ramírez' },
  { ref: '55555555', name: 'Camila Torres' },
  { ref: '44444444', name: 'Mateo Salazar' },
  { ref: '33333333', name: 'Lucía Fernández' },
  { ref: '22222222', name: 'Sebastián Rojas' },
  { ref: '11111111', name: 'Andrea Paredes' },
  { ref: '88888888', name: 'Joaquín Huamán' },
  { ref: '99999999', name: 'Fernanda Castillo' },
  { ref: '12121212', name: 'Renzo Vargas' },
];

function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Contactos cuyo número o nombre contienen lo escrito (sin distinguir tildes ni mayúsculas). */
export function filterContacts(query: string): MockContact[] {
  const needle = normalize(query.trim());
  if (!needle) {
    return MOCK_CONTACTS;
  }
  return MOCK_CONTACTS.filter((contact) => contact.ref.includes(needle) || normalize(contact.name).includes(needle));
}
