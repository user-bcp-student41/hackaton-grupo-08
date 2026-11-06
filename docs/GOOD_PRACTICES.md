# Buenas prácticas del proyecto

Guía derivada del [README](../README.md), la épica [MVP de wallet en Perú](./epic/peru-wallet-mvp.sp.md) y las [user stories](./user-stories/). Contrato del mock: [bcp-wallet-mock-openapi.yaml](./bcp-wallet-mock-openapi.yaml).

> Prioridad de contingencia (README §3.3): primero **W1 (registro/KYC)** y **W2 (pago a contacto)** funcionales y probados; el panel de colecta nunca debe bloquear el flujo principal.

## 1. Alcance y producto

- Construir W1 → W2 de extremo a extremo antes de añadir nada más; no intentar una wallet completa ni una plataforma de recaudación compleja.
- Respetar lo "fuera de alcance": sin dinero real ni integraciones financieras reales, una sola colecta de demo, sin reportes contables avanzados, recordatorios ni funciones sociales.
- El panel de colecta es una funcionalidad propia de la app; **no atribuirla al mock oficial**.
- Toda tarea debe trazarse a una user story o criterio (`W1-0x`, `W2-0x`, `COL-0x`). Si no se traza, no entra en el sprint.
- Las preguntas abiertas de las user stories y la cobertura de W3–W6 se confirman con el PO/mentor; no se asumen ni se declaran fuera de alcance sin autorización.
- Las hipótesis (p. ej. el dolor de la colecta escolar) se presentan como hipótesis a validar, no como hechos.

## 2. Arquitectura y diseño

- Separar capas: UI (`web/`), backend/orquestación (`api/`), cliente del mock y módulo de colecta. La UI no llama directamente al mock si puede evitarse.
- **No asumir un único riel de procesamiento** (épica): encapsular el proveedor tras una interfaz/adaptador para poder cambiar de riel o proveedor.
- Mantener la URL del mock y toda configuración en variables de entorno (`compose.yaml`), nunca en código.
- Mantener el contrato del mock como fuente de verdad; generar o tipar los clientes a partir de `bcp-wallet-mock-openapi.yaml` y no inventar campos.
- Preferir simplicidad: decisiones técnicas registradas en un breve log de decisiones (ADR corto) y documentadas en el README antes del freeze.
- Diseñar para estados **asíncronos** de pago (callbacks, `/resolve`, polling) desde el inicio.

## 3. Modelo de estados de pago y KYC

- Definir un único conjunto de estados (`PENDING`, `SUCCEEDED`, `REJECTED`, `REVERSED`, según el mock) y reutilizarlo en backend, UI y reportes.
- Usar enumeraciones tipadas; nunca comparar con strings sueltos.
- Modelar transiciones permitidas explícitamente y rechazar las inválidas.
- Reglas de negocio clave:
  - Solo los pagos `SUCCEEDED` suman al total confirmado de la colecta (W2-02, W2-03, COL-02).
  - El comprobante solo se muestra en `SUCCEEDED` o `REVERSED`; nunca en `PENDING` o `REJECTED`.
  - Wallet con KYC pendiente/rechazado/no disponible no activa ni permite iniciar pagos (W1-02 a W1-04).
- Mostrar al usuario un único resultado terminal por intento (exitoso, fallido o expirado) con la siguiente acción clara (reintentar o volver).

## 4. Idempotencia y confiabilidad financiera

- Enviar el header `Idempotency-Key` (1–200 caracteres, única por wallet) en **cada** `POST /payments`.
- Generar la clave una vez por intento de usuario (pagador + monto + destino + ventana) y **reutilizarla en los reintentos**; no generar una nueva en cada reintento.
- Una solicitud repetida con la misma clave debe devolver el resultado original; manejar `409 IDEMPOTENCY_CONFLICT` (misma clave con payload distinto) como error explícito.
- Deshabilitar el botón de pago mientras hay una solicitud en vuelo para evitar doble envío.
- Registrar solicitudes duplicadas con `requestId` y `correlationId` para auditoría, y distinguir intentos originales de reintentos en conciliación.
- Definir la ventana de retención de claves como configuración (pregunta abierta en la story de idempotencia).
- Aplicar timeouts, reintentos acotados con backoff y manejo explícito de fallos del servicio (KYC no disponible, errores 5xx).

## 5. Seguridad y datos

- **Cero secretos y cero datos personales reales** en el repositorio, logs, fixtures y capturas; usar datos ficticios (montos y usuarios de prueba).
- `.env` fuera de Git, con un `.env.example` sin valores reales.
- Validar todas las entradas en backend (monto, moneda `PEN`, destino, límites por proveedor), aunque la UI también valide.
- Validar los montos antes del envío y mostrar los límites por proveedor, que pueden estar por debajo del máximo regulatorio.
- Verificar autorización: un usuario solo ve sus pagos, historial y comprobantes; el comercio solo ve sus transacciones QR.
- No exponer detalles internos en errores; mensajes accionables para el usuario y detalle técnico solo en logs.
- No presentar estados engañosos (p. ej. "pagado" cuando está `PENDING`).
- Puertos publicados solo en `127.0.0.1` (como en `compose.yaml`); mantener dependencias actualizadas y revisar `npm audit`.
- Las acciones de soporte (reversos, disputas) quedan registradas con referencia de transacción y estado de resolución.

## 6. UX y mensajes

- Onboarding guiado: mostrar los pasos requeridos y el paso actual siempre; bloquear el avance ante datos faltantes o inválidos con mensajes accionables.
- Confirmación previa al pago con **identidad del destino y monto** visibles.
- Etiquetas de estado claras y consistentes en español; centralizarlas en un único archivo (están por definir con producto).
- Distinguir en la ayuda al usuario una falla técnica de una transferencia ya ejecutada (no hay cancelación unilateral garantizada de P2P).
- Mostrar referencia de transacción en pagos exitosos y persistirla en el historial.
- Accesibilidad básica: etiquetas, foco, contraste y estados de carga/error visibles.

## 7. Observabilidad y analítica

- Instrumentar eventos del funnel: inicio de onboarding, fin de cada paso, cierre del flujo, inicio y resultado de checkout, y evento de activación en pago exitoso (base para MAU).
- Estandarizar nombres de eventos y timestamps (UTC, ISO-8601) para calcular el tiempo de onboarding (8 → 3 min) y la caída en checkout (30% → 8%).
- Logs estructurados con `correlationId`/`requestId` en cada petición; nunca registrar datos sensibles.
- Exponer los datos de atribución de gasto necesarios para MAU (1,000–2,000) y CAC (PEN 10).
- Definir oficialmente las fronteras de inicio/fin del onboarding con el PO (pregunta abierta).

## 8. Conciliación y operación

- Persistir intención de pago, resultado del proveedor y asiento de wallet para poder compararlos.
- Marcar cada descalce con referencia de transacción, tipo y estado de investigación.
- Permitir filtros por fecha, referencia y canal; generar resumen diario (conciliados, no conciliados, resueltos).
- Guardar códigos de motivo legibles por máquina para pagos fallidos/expirados y exponer elegibilidad (reintento, investigación, disputa).
- Mantener `GET /health` operativo y usar `/scenarios` del mock para pruebas deterministas.

## 9. Pruebas

- Cubrir como mínimo los casos del README: `W1-01..04`, `W2-01..04`, `COL-01..02`.
- Probar todos los escenarios del mock: aprobado, pendiente, rechazado, no disponible, callbacks y `/resolve`.
- Probar explícitamente idempotencia: mismo pago reenviado no crea otra operación ni suma dos veces al total.
- Pirámide: pruebas unitarias para reglas (estados, totales, claves), de integración contra el mock y E2E para W1 → W2.
- Las pruebas deben ser deterministas (sin depender de tiempo real ni de red externa) y ejecutables con un solo comando.
- Guardar evidencias (resultados, capturas o logs) para la demo y el freeze.
- Ejecutar los tests antes de cada merge: `cd web; npm test`; y `npm run build` para verificar compilación.

## 10. Calidad de código

- TypeScript en modo estricto; evitar `any`; modelar respuestas del API con tipos.
- Componentes pequeños, separar lógica de negocio de la presentación (servicios/stores), y mantener funciones puras para cálculos (total confirmado, porcentaje de avance).
- Manejo de errores centralizado (interceptor HTTP) con mensajes de usuario mapeados por código de error.
- Dinero: manejar importes de forma consistente (céntimos enteros o decimales controlados), nunca con aritmética flotante sin control; moneda `PEN`.
- Nombres de código en inglés y consistentes; textos de UI y documentación en español.
- Formato y lint automáticos antes de cada commit; sin código muerto ni comentarios obvios.

## 11. Flujo de trabajo y Git

- Ramas cortas por historia (`feature/w1-kyc`, `fix/idempotency-retry`) y PRs pequeños y revisados por al menos otro rol.
- Commits descriptivos que referencien el criterio o story (p. ej. `feat(w2): reutilizar Idempotency-Key en reintentos`).
- `main` siempre ejecutable; integrar con frecuencia para evitar conflictos antes del freeze.
- Definición de "hecho": criterios de aceptación cumplidos, pruebas pasando, revisión de seguridad, documentación actualizada y demo reproducible.
- No introducir cambios de último minuto después del freeze (15:15).

## 12. Colaboración por rol

| Rol | Práctica clave |
|---|---|
| Product Owner | Mantener criterios de aceptación y alcance; resolver preguntas abiertas y coordinar con el mentor. |
| Arquitecto | Validar el contrato del mock, definir integraciones y evitar complejidad innecesaria. |
| Seguridad | Revisar secretos, datos, accesos, validaciones y estados no engañosos en cada incremento. |
| Developer | Implementar lo priorizado, integrar según contrato y mantener el código ejecutable. |
| AI Engineer | Dar contexto claro a la IA y **revisar críticamente** todo código generado antes de integrarlo. |
| Testing | Cubrir los casos W1/W2/COL, reintentos y evidencias; verificar que solo pagos exitosos suman. |

Cada tarea tiene un responsable principal, aunque los roles colaboren.

## 13. Documentación y entrega (Freeze 15:15)

- README actualizado con instrucciones de ejecución (`docker compose up` o scripts) y variables de entorno requeridas.
- Diagrama/descripción breve de arquitectura y registro de decisiones.
- Evidencias de pruebas y escenarios de demo reproducibles (usuario de prueba, escenario del mock por caso).
- Lista de limitaciones conocidas y supuestos pendientes (KYC, límites por proveedor, SLA de soporte, categorías de disputa).

## 14. Checklist previa a un PR

- [ ] Traza a una user story/criterio y respeta el alcance.
- [ ] Estados de pago/KYC usan las enumeraciones compartidas.
- [ ] Los pagos envían `Idempotency-Key` y los reintentos la reutilizan.
- [ ] Solo pagos `SUCCEEDED` suman a la colecta; comprobante solo en `SUCCEEDED`/`REVERSED`.
- [ ] Sin secretos ni datos personales reales.
- [ ] Errores y estados claros y accionables para el usuario.
- [ ] Pruebas nuevas/actualizadas pasan y el build compila.
- [ ] Eventos analíticos y logs con `correlationId` donde aplique.
- [ ] Documentación actualizada.
