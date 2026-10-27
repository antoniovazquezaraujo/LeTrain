# ADR-037: Cola de construcción de rutas

## Estado: ACEPTADO

> Implementado en `core` y en el cliente 2D dentro de la rama `feature/construction-queue`.
> Depende de [[ADR-034-Route-Planner-Cost-Aware|ADR-034]],
> [[ADR-035-Route-Preview-2D|ADR-035]] y [[ADR-036-Async-Route-Preview|ADR-036]].
>
> Esta ADR describe **lo que existe hoy**: elegir un plan en la preview y construirlo
> inmediatamente mediante una cola automática. La separación entre presupuestar y ejecutar, las
> constructoras, las zonas de exclusión, el mantenimiento y la calidad de materiales son ideas
> posteriores y no forman parte de esta implementación. Están recogidas en
> [[ADR-038-Planned-Works-and-Contractors|ADR-038]] y
> [[ADR-039-Track-Quality-and-Maintenance|ADR-039]].

## Contexto

La preview 2D de ADR-035/036 permite elegir dos extremos, calcular varias políticas y seleccionar
un `RoutePlan`. Falta convertir ese plan en vía real sin bloquear el bucle de juego y sin meter
lógica de presentación en el planificador.

En el momento de escribir esta ADR, el dueño quiere un flujo deliberadamente simple:

- se elige una ruta en la preview;
- al aceptarla, la construcción empieza inmediatamente;
- no hay lista de presupuestos, constructoras ni varias obras a la vez;
- la ruta se construye de forma aislada y el jugador la conecta manualmente después;
- los trenes no se bloquean: si entran en una vía en obras, las consecuencias son suyas.

La separación entre planificación y ejecución se considerará más adelante. Esta ADR no la
anticipa.

## Decisión

### Flujo actual

1. El usuario entra en `CONNECT` y calcula un plan.
2. Si el plan es factible y construible, `Enter` lo selecciona.
3. El presenter abandona `CONNECT` y crea una obra automática.
4. La obra avanza con el reloj de simulación, una celda cada cierto número de ticks.
5. La vía aparece en el mapa conforme se construye. Las celdas pendientes se muestran como
   fantasmas y A/B siguen visibles.
6. Al terminar, la edición manual vuelve a estar disponible.
7. Si se cancela, la obra se detiene y la vía parcial permanece en el mundo.

No se puede empezar una segunda obra mientras la primera está activa. El trabajo se ejecuta en el
hilo de UI; no hay hilos de construcción.

### Núcleo: `letrain.construction`

El paquete es UI-free. No conoce `Model`, `RailTrackMaker`, `View` ni Lanterna.

- `ConstructionState`: `IDLE`, `REJECTED`, `BUILDING`, `DONE`, `CANCELLED`.
- `ConstructionProgress`: estado, celdas construidas, total, paso actual, ticks restantes y motivo
  de rechazo/cancelación.
- `TrackMaterializer`: puerto con `prepare(plan)`, `buildStep(plan, index, previous, next)` y
  `finish()`. El preflight no debe mutar el mundo.
- `RouteConstructionJob`: materializa un único plan, una celda por tick disponible. El coste de
  una celda es `baseDelayTicksPerCell + economy.getConstructionDelay(trackType)`.
- `RouteConstructionQueue`: admite como máximo un job `BUILDING`, expone progreso, cancelación y
  `ConstructionGuard.isEditingLocked()`.
- `ConstructionGuard`: consulta para que la UI bloquee la edición manual sin meter un flag global
  en `Model`.

### Materialización real

`RailTrackMaterializer` es el adaptador de terminal. Usa la maquinaria existente
(`RailTrackMaker`/`TurtleBuilder`) para no duplicar la creación de vías, puentes, túneles y gates.

Antes de construir:

- comprueba todas las celdas del plan;
- si alguna ya tiene vía o elemento ferroviario, rechaza la obra con un motivo legible;
- no modifica nada durante el preflight.

Durante la construcción:

- coloca exactamente una celda por paso, en el orden del plan;
- encadena solo las celdas creadas por esta obra;
- no se acopla a vías existentes, no crea forks y no conecta estaciones;
- restaura la posición, dirección y modo del cursor del autor;
- suprime el journal para no contaminar undo/redo.

La obra básica construye una **ruta aislada**. El jugador puede conectarla manualmente a la red
cuando termine. Los cruces automáticos, la reutilización de vía y las bifurcaciones quedan fuera de
esta implementación.

### Tiempo y coste actuales

- El coste que se cobra es el coste real de las celdas construidas, calculado por la economía
  existente. No hay recargo de constructora ni presupuesto separado en esta fase.
- La base actual es **1 tick por celda**, más el delay real de puente/túnel.
- No se inventan precios ni tiempos: el HUD usa el `RoutePlan` y el progreso reales.
- Cancelar no devuelve el dinero de lo construido; lo construido se queda.

### UI 2D

- La preview avisa **antes de seleccionar** si el plan pisa una vía o elemento existente. En ese
  caso `Enter` queda deshabilitado y el autor sigue en `PREVIEW` para cambiar de política,
  extremos o cancelar.
- La obra muestra las celdas pendientes como círculos fantasma y A/B como referencias.
- La barra de estado muestra celdas construidas/total, coste, tiempo y `Esc cancel`.
- Se permite navegar, hacer zoom, cambiar de página y usar los modos, incluido `Drive`. Los trenes
  no se restringen.
- La edición manual de vías e infraestructura está **bloqueada globalmente** mientras la obra está
  activa. La consola permite navegación/consulta, pero no comandos de edición, guardado, carga,
  nueva partida, undo/redo o quit.
- Los multiplicadores de movimiento (dígitos y `Space` para resetear) siguen disponibles en
  `RAILS`.
- El menú File está bloqueado durante la obra: `Esc` abre una confirmación en dos pasos. El primer
  `Esc` pausa la cola y pide confirmación; el segundo cancela, y cualquier otra tecla reanuda.
- Tras terminar o cancelar, la edición manual se libera y `Esc` vuelve a abrir el menú File normal.

### Límites deliberados de esta fase

- Una sola obra activa.
- Sin lista de planes ni presupuestos.
- Sin constructoras, cotizaciones ni recargos.
- Sin reservas de zona ni construcción manual fuera de la obra.
- Sin varias obras simultáneas.
- Sin cruces automáticos, forks, estaciones, sensores o señalización.
- Sin mantenimiento ni calidad de vía.
- Sin comando `connect`.
- Sin sonido de obra.

Estas ausencias son intencionadas, no defectos pendientes de esta ADR.

## Consecuencias

- La construcción está separada en una máquina de estados pequeña y testeable, con un adaptador
  que reutiliza la maquinaria de vías existente.
- La UI puede bloquear la edición sin ensuciar `Model`: el bloqueo es una consulta de la cola.
- La ruta se construye de forma aislada; el jugador conserva el control de la conexión final.
- El flujo actual es deliberadamente inmediato: planificar y construir es una sola acción.
- Cualquier coste de automatización, lista de obras o constructora pertenece a una fase posterior.

## Futuro

- [[ADR-038-Planned-Works-and-Contractors|ADR-038]]: lista de planes, presupuestos, constructoras,
  zonas de exclusión, varias obras, efectivo y cancelación.
- [[ADR-039-Track-Quality-and-Maintenance|ADR-039]]: materiales, ritmo de construcción manual,
  calidad y mantenimiento.
- [[Construction-Roadmap|Roadmap de construcción]]: orden propuesto y dependencias.
