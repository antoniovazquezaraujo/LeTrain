# ADR-037: Cola de construcción de rutas (core)

## Estado: PROPUESTO

> Rebanada de core + tests. No hay cambios de UI/terminal todavía; el presenter se cablea en la
> siguiente rebanada. Depende de [[ADR-034-Route-Planner-Cost-Aware|ADR-034]],
> [[ADR-035-Route-Preview-2D|ADR-035]] y [[ADR-036-Async-Route-Preview|ADR-036]].

## Contexto

ADR-034 dejó un `RoutePlan` puro (pasos, coste y ticks reales) y ADR-035/036 dejaron la
previsualización y la planificación asíncrona. Falta convertir un plan aceptado en vía real sin
meter lógica de UI en el motor y sin que la operación bloquee la simulación.

El dueño fijó estas reglas para la capa de construcción:

- un plan a la vez, una sola obra/cursor a la vez;
- cancelar detiene la obra y deja lo ya construido;
- sin reutilizar vía existente ni crear forks;
- la celda de origen también se construye;
- tiempo base por celda (1 tick por defecto) sumado a los delays reales de puente/túnel.

## Decisión

### Paquete `letrain.construction` (core, UI-free)

- `ConstructionState`: `IDLE`, `REJECTED`, `BUILDING`, `DONE`, `CANCELLED`.
- `ConstructionProgress`: record inmutable con estado, celdas construidas/total, paso actual
  (nullable), ticks restantes del paso actual y motivo de rechazo/cancelación. `IDLE` lo usa una
  cola vacía; nunca se inventan costes.
- `TrackMaterializer`: puerto con `prepare(plan)`, `buildStep(plan, index, previous, next)` y
  `finish()`. El core no conoce `Model`, `RailTrackMaker`, `View` ni Lanterna.
- `RouteConstructionJob`: materializa **un** plan, celda a celda, con un tick de juego por avance.
- `RouteConstructionQueue`: dueña de como máximo un job activo, más `ConstructionGuard`
  (`isEditingLocked()`) para que la UI futura bloquee la edición manual sin tocar el `Model`.

### Preflight

`prepare(RoutePlan)` se ejecuta una sola vez al crear el job. Debe ser **no mutante**: si devuelve
un motivo, el job queda `REJECTED`, no se llama a `buildStep` y no se gasta nada. Un plan
infactible se rechaza con el `reason()` del planner sin llegar a llamar al materializador. Una
excepción del preflight se convierte en rechazo con mensaje, para no romper el bucle de juego.

La validación de celdas ocupadas por vía/forks/estaciones pertenece al adaptador (siguiente
rebanada); esta rebanada solo define el puerto y el camino de rechazo.

### Ticks

Cada celda cuesta `baseDelayTicksPerCell + economy.getConstructionDelay(trackType)` ticks.

- Por defecto `baseDelayTicksPerCell = 1`; vía normal tiene delay 0, así que se construye **una
  celda por tick**.
- Puente y túnel suman su delay configurado real al tiempo base.
- Como máximo se construye una celda por `tick()`. Una celda de coste 0 se construye en el primer
  tick; una de coste `n` en el tick `n`.
- `tick()` fuera de `BUILDING` es no-op: no se puede construir dos veces la misma celda ni seguir
  después de `DONE`, `REJECTED` o `CANCELLED`.
- El job es determinista y no depende del reloj de pared; solo avanza cuando el llamador hace
  `tick()`.

### Cancelación y fin

`cancel()` detiene el job al instante, pasa a `CANCELLED` y conserva `builtSteps`. Las celdas ya
construidas siguen en el mundo; el adaptador es responsable de ellas. `finish()` se llama una vez
al terminar (plan vacío, `DONE` o `CANCELLED`) y debe ser seguro incluso sin celdas construidas.
`cancel()` y `tick()` son idempotentes después de un estado terminal.

### Ruta aislada

La cola construye una **ruta aislada**: no se acopla a vía existente, no crea forks ni conecta
estaciones. El plan puede cruzar vía existente en diagonal o perpendicular, pero no se fusiona con
ella. Cruces, bifurcaciones y reutilización de vía quedan **fuera de alcance** de este flujo.

Los trenes no se bloquean por la obra y no hay lógica de trenes en este paquete.

### Cola

- `start(plan, materializer)` crea un job si no hay otro `BUILDING`; si lo hay, devuelve `false` y
  no toca el job activo.
- Un job terminal (`DONE`, `REJECTED`, `CANCELLED`) no bloquea: el siguiente `start` lo sustituye,
  pero se conserva el último `progress()`/`job()` hasta entonces.
- `isActive()`/`isEditingLocked()` solo son ciertos en `BUILDING`; así un rechazo o una obra
  terminada no dejan la edición bloqueada.
- `tick()` sin job devuelve `ConstructionProgress.idle()`.

## Fuera de alcance de esta rebanada

- Cambios de terminal/Lanterna, HUD, menús, confirmación con `Esc` o materializador real.
- Bloqueo de comandos/consola durante la obra.
- Reutilización de vía, forks, cruces, estaciones, sensores o señalización.
- Snapshot del `RailMap`, cambios en el planner o integración con escenarios/`connect`.
- Coste económico dentro del job: el dinero lo gestiona el adaptador con la maquinaria existente.

## Consecuencias

- El motor gana una pieza pequeña, determinista y testeable sin `Model`, con un puerto que la UI
  puede implementar encima de `RailTrackMaker` en la siguiente rebanada.
- La semántica de tiempo es explícita y coincide con lo que muestra la preview: ticks del planner
  más un tick base por celda.
- Cancelar nunca “deshace” vía: deja la obra a medias, como pidió el dueño.
- El bloqueo de edición es una consulta, no un estado global; no contamina el `Model`.
