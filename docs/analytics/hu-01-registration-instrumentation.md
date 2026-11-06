# HU-01 · Instrumentación del registro (propuesta, pendiente de Producto)

Correo del OTP: **simulado** (`SimulatedOtpSender`); no sale ningún correo real. El código solo queda en un buzón en memoria con `registration.mailbox.enabled=true` (perfiles dev/test).

## Eventos (reutilizan `AnalyticsEvent`; `sessionId` = `registrationId`, `userId` = `customerId` seudónimo)
| Evento | Cuándo | Campos |
|---|---|---|
| `ONBOARDING_STARTED` | Paso 1 válido (`POST /registrations`) | `acquisitionSource`, `occurredAt` |
| `ONBOARDING_STEP_COMPLETED` | Fin Paso 1 (`PERSONAL_DATA`) y Paso 2 (`OTP_VERIFICATION`) | `stepCode` |
| `ONBOARDING_CLOSED` | OTP válido | `outcome=COMPLETED` |

Sin documento, correo, contraseña ni OTP. Un fallo al registrar un evento no altera la respuesta.

## Cálculos propuestos (frontera sin aprobar, P4/P5)
- Duración (HU1-AC5): `CLOSED.occurredAt − STARTED.occurredAt` por `sessionId`; mediana por cohorte trimestral.
- Nuevos usuarios activos (HU1-AC6): conteo mensual de `ONBOARDING_CLOSED` con `outcome=COMPLETED`.
- CAC: atribución por `acquisitionSource`; el gasto de adquisición es una fuente externa aún no definida.
No se afirma el cumplimiento de 8→3 min, MAU 1,000–2,000 ni CAC PEN 10.
