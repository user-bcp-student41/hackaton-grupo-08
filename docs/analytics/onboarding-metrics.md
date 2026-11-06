# Analítica de onboarding (HU-01) — PROPUESTA, no definición aprobada

Estado: instrumentación **propuesta**. Producto debe aprobar la frontera de medición, la definición de
"usuario activo" y la fuente de gasto de adquisición. Las metas (mediana 8 → 3 min, MAU 1.000–2.000, CAC PEN 10)
son metas de negocio del trimestre posterior al lanzamiento: **este cambio no las declara cumplidas**, solo
permite medirlas.

## Eventos (`AnalyticsEvent`, append-only)

| Campo | Notas |
|---|---|
| `eventId` | UUID, clave de deduplicación |
| `eventType` | `ONBOARDING_STARTED`, `ONBOARDING_STEP_COMPLETED`, `ONBOARDING_CLOSED` |
| `occurredAt` | UTC, reloj del servidor |
| `userId`, `sessionId` | seudónimo, sin PII |
| `stepCode` | solo en paso completado |
| `outcome` | solo en cierre (`COMPLETED`; `ABANDONED` no implementado) |
| `acquisitionSource` | `UNATTRIBUTED` si falta |

Los eventos no contienen datos del formulario.

## Duración (HU1-AC5)

Propuesta: `duración = t(ONBOARDING_CLOSED con outcome=COMPLETED) − t(ONBOARDING_STARTED)` por `sessionId`;
mediana sobre las sesiones cerradas del periodo. Las sesiones sin cierre `COMPLETED` se excluyen de la mediana y se
cuentan aparte. Implementado en `OnboardingMetricsService.sessionDurations` / `durationReport`.

## Nuevos usuarios activos por mes y fuente (HU1-AC6)

Propuesta: usuario activo nuevo = usuario con `ONBOARDING_CLOSED(COMPLETED)`; se asigna al mes calendario de la fecha
de cierre (UTC por defecto; zona horaria por definir) y se agrupa por `acquisitionSource`. Implementado en
`OnboardingMetricsService.newActiveUsersByMonth`. Abierta la ambigüedad con "activación del pagador" (HU-02).

## CAC

**Bloqueado**: requiere una fuente de gasto de adquisición por `acquisitionSource` y mes (Producto/PO). Fórmula
prevista: `CAC(mes, fuente) = gasto(mes, fuente) / nuevos usuarios activos(mes, fuente)`. No se calcula en este cambio.

## Consulta SQL equivalente (cuando exista base de datos)

```sql
-- duración por sesión
SELECT s.session_id,
       c.occurred_at - s.occurred_at AS duration
FROM analytics_event s
JOIN analytics_event c ON c.session_id = s.session_id
 AND c.event_type = 'ONBOARDING_CLOSED' AND c.outcome = 'COMPLETED'
WHERE s.event_type = 'ONBOARDING_STARTED';

-- nuevos usuarios activos por mes y fuente
SELECT date_trunc('month', occurred_at) AS month, acquisition_source, count(DISTINCT user_id)
FROM analytics_event
WHERE event_type = 'ONBOARDING_CLOSED' AND outcome = 'COMPLETED'
GROUP BY 1, 2;
```

## Limitación actual

Los eventos se guardan en memoria (no durables) tras la interfaz `AnalyticsEventRepository`; no hay motor de base de
datos en el repositorio.
