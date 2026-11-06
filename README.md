# AI Labs

## Web

```powershell
cd web
npm install
npm start
```

Angular queda disponible en `http://localhost:4200`.

## API

```powershell
cd api
mvn quarkus:dev
```

El endpoint REST imperativo es `GET http://localhost:8080/hello` y responde `Hello World`.

Para ejecutar las pruebas de la API:

```powershell
mvn "-Dquarkus.http.test-port=8082" test
```

El puerto alternativo evita el conflicto con otro servicio que ya ocupa `8081` en este entorno.
# Día 5 — Plan de trabajo del hackathon BCP
## G1: Scope Check | 09:20–10:00
### Propuesta: seguimiento de aportes voluntarios para una promoción escolar

## Objetivo de la planificación

Definir el alcance de la wallet, los roles del equipo y las actividades de los Sprint 1 y 2 para construir una demostración funcional.

La propuesta consiste en una aplicación de demostración para ayudar a organizar los aportes voluntarios de las familias para una promoción escolar en Perú. Permitirá simular aportes, consultar el estado de los pagos y visualizar el avance de la colecta.

Se utilizarán las historias oficiales **W1 — Registro y activación** y **W2 — Pago a contacto** como base técnica. El panel de colecta será una funcionalidad adicional de nuestra aplicación, no una capacidad que debamos atribuir al mock oficial.

---

# 1. Punto 1 — ¿Hasta dónde llega nuestro alcance de la wallet?

## 1.1 Problema que queremos resolver

En Perú, los padres de familia pueden organizar aportes voluntarios para financiar una actividad de promoción escolar, como una celebración de fin de año, los recuerdos de la promoción o una reunión de despedida.

El seguimiento de los aportes puede volverse difícil cuando la información está distribuida entre conversaciones de WhatsApp, transferencias y comprobantes enviados individualmente. La persona organizadora necesita saber qué aportes se han registrado, cuánto dinero se ha reunido y qué pagos aún están pendientes de confirmar.

**Problema propuesto:**

> La organización de una colecta para una promoción escolar puede resultar desordenada cuando no existe un lugar centralizado para registrar los aportes y consultar el estado de los pagos.

Esta es una **hipótesis de diseño que debemos validar** con al menos un padre o una madre que haya participado en la organización de una promoción. No afirmaremos que todas las familias tienen esta dificultad.

## 1.2 Usuario principal

**El padre o la madre que organiza los aportes voluntarios de la promoción escolar.**

Sus necesidades principales son:
- Registrar los aportes.
- Conocer el estado de los pagos.
- Consultar el monto reunido.
- Identificar los aportes pendientes de confirmación.

Los padres y madres que realizan los aportes serán usuarios secundarios.

## 1.3 Valor esperado

La solución debería permitir que la persona organizadora:
- Consulte los aportes registrados en un solo lugar.
- Distinga los pagos confirmados de los pendientes o rechazados.
- Conozca cuánto se ha reunido frente al objetivo de la colecta.
- Reduzca la necesidad de revisar manualmente conversaciones y comprobantes.

A los padres que aportan les permitirá consultar el resultado del pago y acceder al comprobante cuando esté disponible.

**Valor esperado:** mejorar la trazabilidad de los aportes voluntarios y facilitar el seguimiento de la colecta, sin manejar dinero real en el prototipo.

## 1.4 Ejemplo cotidiano para la demostración

Imaginemos que un grupo de familias organiza una promoción escolar y establece una meta ilustrativa de **S/ 3,000** para la celebración.

Una familia quiere realizar un aporte de **S/ 50**. La aplicación simula el pago, permite consultar su estado y, si queda confirmado como exitoso, muestra el comprobante. La persona organizadora ve el aporte registrado y el avance actualizado de la colecta.

Los montos son ficticios y sirven únicamente para la demostración.

## 1.5 Flujo principal

1. La familia se registra.
2. Se valida y activa la wallet mediante el KYC simulado (W1).
3. Se simula un aporte a la persona organizadora mediante pago a contacto (W2).
4. Se consulta el resultado, el historial y el comprobante cuando corresponda.
5. La aplicación actualiza la colecta con los aportes confirmados.

## 1.6 Límite del alcance

### Incluido en el hackathon
- **W1:** registro y activación de la wallet mediante KYC simulado.
- **W2:** pago a contacto mediante el proveedor simulado.
- Visualización de pagos exitosos, pendientes y rechazados.
- Consulta del historial y del comprobante cuando corresponda.
- Panel propio para registrar una colecta, definir una meta y mostrar los aportes confirmados.
- Pruebas de reintentos para evitar pagos duplicados.

### Fuera del alcance
- Gestión integral de la promoción escolar (presupuesto general, proveedores, contratos, gastos y rendición de cuentas).
- Administración de varias colectas con reglas, responsables y permisos avanzados; el prototipo se centrará en una colecta de demostración.
- Recordatorios automáticos y campañas de comunicación masiva para solicitar aportes.
- Funcionalidades sociales como comentarios, votaciones o coordinación de actividades de la promoción.
- Reportes avanzados, exportaciones contables y conciliación financiera completa; se mostrará únicamente el resumen básico necesario para la demo.
- Automatizaciones de cobro, penalizaciones o clasificación de las familias según sus aportes.
- Funcionalidades adicionales que no sean necesarias para demostrar el recorrido priorizado de W1 y W2 y el seguimiento básico de la colecta.

### Decisión pendiente con el mentor
El reto general contempla las seis historias W1–W6. Proponemos priorizar W1 y W2 como flujo principal, pero debemos confirmar qué cobertura funcional mínima se espera para W3, W4, W5 y W6. No conviene declarar esas historias fuera del alcance sin autorización.

---

# 2. Punto 2 — ¿Qué roles vamos a tener?

El equipo trabajará con los seis roles definidos para el hackathon.

## 2.1 Product Owner (PO)
- Define el problema, el usuario principal y el valor esperado.
- Prioriza las historias y mantiene claro el alcance.
- Define y valida los criterios de aceptación con el equipo.
- Resuelve dudas funcionales y coordina las decisiones de alcance con el mentor.

## 2.2 Arquitecto
- Revisa el repositorio, la arquitectura existente y el contrato del mock.
- Define cómo se conectan la interfaz, el backend, la wallet, los pagos y el módulo de colecta.
- Establece las decisiones técnicas y ayuda a evitar complejidad innecesaria.
- Verifica que la solución sea ejecutable y demostrable dentro del tiempo disponible.

## 2.3 Seguridad
- Revisa el manejo de datos y la configuración de la aplicación.
- Comprueba que no se incluyan credenciales, secretos ni datos personales reales.
- Revisa los controles de acceso y las validaciones relevantes para el alcance.
- Verifica que los estados y errores no se presenten de forma engañosa al usuario.

## 2.4 Developer
- Implementa la interfaz y la lógica funcional priorizada por el equipo.
- Integra los endpoints del mock según el contrato disponible.
- Desarrolla el panel de la colecta y el cálculo de aportes confirmados, si el flujo principal ya está estable.
- Corrige errores y mantiene el código integrado y ejecutable.

## 2.5 AI Engineer
- Utiliza herramientas de IA para apoyar la implementación, análisis y resolución de problemas.
- Prepara instrucciones y contexto claros para las tareas que se deleguen a la IA.
- Revisa críticamente el código y las respuestas generadas antes de integrarlos.
- Ayuda a incorporar agentes, herramientas o flujos de IA cuando aporten valor demostrable al reto.

## 2.6 Testing
- Diseña y ejecuta pruebas para los flujos de W1 y W2.
- Valida los escenarios aprobados, pendientes, rechazados y los errores del servicio.
- Comprueba los reintentos y que una misma operación no se contabilice dos veces.
- Verifica que solo los pagos exitosos se sumen al total de la colecta y registra evidencias.

## 2.7 Colaboración entre roles
- El PO define qué se debe construir y cómo se aceptará.
- El Arquitecto define la solución técnica y sus integraciones.
- Seguridad revisa los riesgos y controles durante el desarrollo.
- Developer implementa la funcionalidad.
- AI Engineer apoya el desarrollo con IA y valida lo generado.
- Testing prueba cada incremento junto con el resto del equipo.

Los roles pueden colaborar en una misma tarea, pero cada uno debe tener una responsabilidad principal clara.

---

# 3. Punto 3 — Actividades del Sprint 1 y Sprint 2

Según el cronograma del Día 5:
- **Sprint 1:** 10:15–12:30.
- **G2 Scope Check:** 13:30–13:45.
- **Sprint 2:** 13:45–15:15.
- **Freeze:** 15:15.

## 3.1 Sprint 1 — Construir el flujo principal

**Horario:** 10:15–12:30

**Objetivo:** conseguir que el registro, la activación y el pago básico funcionen de extremo a extremo con el mock.

| Actividad | Responsable principal | Entregable |
|---|---|---|
| Revisar repositorio, contrato y URL del mock | Arquitecto/a | Integración identificada y probada |
| Preparar tareas técnicas a partir de W1 y W2 | Product Owner | Historias y criterios priorizados |
| Implementar registro y KYC simulado | AI Engineer | Flujo W1 ejecutable |
| Implementar pago a contacto | AI Engineer | Flujo W2 conectado al mock |
| Probar KYC aprobado y pago exitoso | QA & Security | Evidencia de la ruta feliz |
| Preparar instrucciones y registrar avances | Presenter | README inicial y registro de decisiones |

### Entregable al terminar el Sprint 1

Una versión ejecutable que permita:
1. Registrar un usuario de prueba.
2. Activar la wallet mediante el KYC simulado.
3. Crear un pago a contacto mediante el mock.
4. Consultar el resultado de la operación.

La prioridad es lograr primero un recorrido funcional, no terminar toda la aplicación.

## 3.2 G2 — Scope Check

**Horario:** 13:30–13:45

Antes de iniciar el segundo sprint, revisaremos:
- Si el flujo W1 → W2 funciona de principio a fin.
- Si el alcance sigue siendo viable.
- Qué errores o bloqueos debemos resolver.
- Qué cobertura de W3–W6 exige el mentor.
- Qué funcionalidades adicionales pueden completarse sin poner en riesgo la demo.

## 3.3 Sprint 2 — Robustecer, completar y preparar la demostración

**Horario:** 13:45–15:15

**Objetivo:** convertir el flujo inicial en una demo más robusta, con manejo de errores y una visualización básica de la colecta.

| Actividad | Responsable principal | Entregable |
|---|---|---|
| Completar consulta de estado e historial | AI Engineer | Pantallas de resultado e historial |
| Mostrar comprobante cuando corresponda | AI Engineer | Comprobante del pago exitoso |
| Probar pagos pendientes, rechazados y reintentos | QA & Security | Pruebas de excepciones e idempotencia |
| Implementar panel de colecta si el flujo base está estable | AI Engineer + Product Owner | Meta y total confirmado |
| Corregir errores críticos y validar el recorrido | Todo el equipo | Demo reproducible |
| Actualizar README, arquitectura y evidencias | Presenter + Arquitecto/a | Documentación lista para el freeze |

### Entregable al terminar el Sprint 2

Una demo que muestre:
- Registro y activación de la wallet.
- Pago simulado a contacto.
- Consulta de estado, historial y comprobante cuando esté disponible.
- Manejo de al menos un escenario de excepción.
- Panel básico de la colecta, si el tiempo y la prioridad acordada lo permiten.
- Pruebas y evidencias que demuestren el comportamiento.

**Prioridad de contingencia:** si el tiempo no alcanza, aseguraremos primero W1 y W2 funcionales y sus pruebas. El panel de la colecta no debe impedir que el flujo principal funcione.

## 3.4 Freeze — 15:15

Antes del freeze debemos tener:
- Una versión estable del código.
- README actualizado.
- Arquitectura e instrucciones para ejecutar la solución.
- Evidencias de las pruebas.
- Una demo reproducible sin depender de cambios de último minuto.

---

# 4. Criterios de aceptación iniciales

Estos criterios son una propuesta para la planificación y deben contrastarse con las historias oficiales antes de considerarlos definitivos.

| ID | Caso de prueba | Resultado esperado |
|---|---|---|
| W1-01 | KYC aprobado | La wallet queda activa. |
| W1-02 | KYC pendiente | La wallet permanece pendiente hasta una decisión. |
| W1-03 | KYC rechazado | La wallet queda rechazada y no permite iniciar el pago. |
| W1-04 | KYC no disponible | Se informa el error sin activar indebidamente la wallet. |
| W2-01 | Aporte simulado exitoso | Se muestra el resultado exitoso y el comprobante cuando esté disponible. |
| W2-02 | Aporte pendiente | No se suma al total confirmado de la colecta. |
| W2-03 | Aporte rechazado | No se suma al total confirmado. |
| W2-04 | Reintento del mismo pago | No se crea una operación duplicada. |
| COL-01 | Consultar la colecta | Se muestra la meta y el total de aportes confirmados. |
| COL-02 | Actualizar un aporte exitoso | El total y el porcentaje de avance se actualizan correctamente. |

En el mock, el comprobante está disponible cuando el pago está en estado `SUCCEEDED` o `REVERSED`; para estados `PENDING` y `REJECTED`, no debe presentarse como disponible.

---

# 5. Preguntas para el mentor en el G1

1. ¿Podemos usar W1 y W2 como recorrido principal y construir la gestión de la colecta como una funcionalidad propia?
2. ¿Qué cobertura funcional mínima debemos tener para W3, W4, W5 y W6?
3. ¿Cuál es la URL del mock asignada a nuestro grupo?
4. ¿Qué pruebas y evidencias se esperan para validar las historias?
5. ¿Qué herramientas, Skills, agentes u orquestación debemos mostrar para evidenciar los aprendizajes de los días 1–4?

---

# 6. Checklist para el G1

- [ ] Alcance definido: W1 y W2 como flujo principal.
- [ ] Fuera de alcance identificado: dinero real e integraciones financieras reales.
- [ ] Pregunta al mentor preparada sobre la cobertura exigida para W3–W6.
- [ ] Roles y responsabilidades asignados.
- [ ] Actividades y entregables del Sprint 1 definidos.
- [ ] Actividades y entregables del Sprint 2 definidos.
- [ ] Entregable mínimo de la demo y evidencias acordados.

---

# 7. Guion breve para explicar la propuesta al mentor

> Nuestro caso de uso es una colecta voluntaria para una promoción escolar. Queremos facilitar el registro y seguimiento de los aportes. Para ello, priorizaremos W1, registro y activación, y W2, pago a contacto, usando el mock oficial. Añadiremos un panel sencillo que muestre la meta y los aportes confirmados.
>
> Hemos definido los roles y dividido el trabajo en dos sprints: primero construiremos el flujo de extremo a extremo; después reforzaremos las excepciones, las pruebas, la documentación y la demo. Queremos confirmar con usted qué cobertura funcional espera para W3–W6 antes de cerrar el alcance.

## Recomendación final

No intentemos construir una billetera completa y una plataforma de recaudación compleja al mismo tiempo. Primero aseguremos el flujo funcional de registro y pago; después conectemos ese pago al panel de la colecta. Así tendremos una demo más coherente, comprobable y fácil de explicar.