/**
 * Datos ESTÁTICOS de demostración para pantallas cuyos módulos aún no existen (inicio, colecta, perfil).
 * Se reemplazan por llamadas a la API cuando esos módulos estén disponibles; nada aquí viene del servidor.
 */
export const MOCK_USER = {
  firstName: 'Ana',
  fullName: 'Ana Rojas',
  documentNumber: '12345678',
};

export const MOCK_BALANCE = 350;

export type CollectionMovementStatus = 'CONFIRMED' | 'PENDING';

export interface CollectionMovement {
  name: string;
  amount: number;
  status: CollectionMovementStatus;
}

export const MOCK_COLLECTION = {
  name: 'Promoción 2026',
  organizer: 'María Organiza',
  raised: 1250,
  goal: 3000,
  myPaid: 100,
  myPending: 50,
};

export const MOCK_COLLECTION_MOVEMENTS: CollectionMovement[] = [
  { name: 'Ana Rojas', amount: 50, status: 'CONFIRMED' },
  { name: 'Luis Pérez', amount: 50, status: 'CONFIRMED' },
  { name: 'Carla Torres', amount: 50, status: 'PENDING' },
  { name: 'Jorge Salas', amount: 50, status: 'CONFIRMED' },
  { name: 'Rosa Quispe', amount: 50, status: 'PENDING' },
  { name: 'Diego Vargas', amount: 50, status: 'CONFIRMED' },
  { name: 'Lucía Mendoza', amount: 50, status: 'CONFIRMED' },
  { name: 'Mateo Flores', amount: 50, status: 'PENDING' },
];
