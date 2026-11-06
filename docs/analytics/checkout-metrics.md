# Analítica de checkout y activación del pagador (HU-02) — PROPUESTA, no definición aprobada

> Los nombres de eventos y los límites del funnel son una **propuesta** hasta que Producto los valide (P5/P10).
> **No se declara cumplida** la meta de caída de checkout de 30% a 8% (HU2-AC5) ni el objetivo MAU 1,000–2,000
> (HU2-AC6): ambas exigen datos de cohorte posteriores al lanzamiento. La demo solo comprueba que los datos
> necesarios para calcularlas se registran.

## Eventos (`CheckoutEvent`, append-only, en memoria)

| Campo | Notas |
|---|---|
| `eventId` | UUID |
| `eventType` | ver catálogo |
| `occurredAt` | UTC, reloj del servidor |
| `userId` | seudónimo, sin PII |
| `paymentId`, `retryOf` | cuando existe intento |
| `providerId`, `channel`, `outcome`, `transactionReference` | según el hito |
| `dedupeKey` | evita duplicar un hito (p. ej. `paymentId` en `PAYER_ACTIVATED`) |

No se guardan nombre ni alias del destinatario, `recipientRef` ni importes. Un fallo al registrar un evento
nunca cambia el resultado del pago.

## Catálogo

| Evento | Origen | Hito |
|---|---|---|
| `CHECKOUT_CONFIRMATION_VIEWED` | UI (`POST /api/v1/checkout-events`) | pantalla de confirmación vista |
| `CHECKOUT_PAYMENT_SUBMITTED` | servidor | intento creado y enviado |
| `CHECKOUT_LIMIT_REJECTED` | servidor | límite de monto superado (422) |
| `CHECKOUT_PENDING_SHOWN` | UI | "procesando" mostrado |
| `CHECKOUT_PAYMENT_TERMINAL` | servidor | resultado terminal (`outcome` = SUCCESS/FAILED/EXPIRED) |
| `CHECKOUT_RETRY_REQUESTED` | servidor | reintento creado |
| `CHECKOUT_LATE_CONFIRMATION_IGNORED` | servidor | éxito tardío del proveedor tras expirar (conciliación) |
| `PAYER_ACTIVATED` | servidor | transacción exitosa (HU2-AC6); una sola vez por `paymentId` |

## Definición propuesta de checkout

- **Inicio del funnel:** `CHECKOUT_CONFIRMATION_VIEWED`.
- **Completado:** `CHECKOUT_PAYMENT_TERMINAL` con `outcome = SUCCESS`.
- **Caída de checkout** = usuarios con inicio sin completado / usuarios con inicio. La ventana de tiempo y si
  un reintento cuenta como el mismo checkout son decisiones de Producto.

## Activación del pagador / MAU

`PAYER_ACTIVATED` se emite por cada pago exitoso. Si la activación cuenta solo el primer pago por pagador es
una pregunta abierta; para MAU basta contar `userId` distintos por mes.

## Limitaciones actuales

- Almacén en memoria: un reinicio pierde los eventos. No hay exportación ni consulta SQL aún.
- Sin umbral de "monto bajo" ni tiempo de expiración definidos por Producto: los valores de `application.properties`
  (`%dev`/`%test`) son solo para la demo.
