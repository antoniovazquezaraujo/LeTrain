# ADR-036: Previsualización asíncrona de rutas largas

## Estado: PROPUESTO

> Depende de [[ADR-034-Route-Planner-Cost-Aware|ADR-034]] y
> [[ADR-035-Route-Preview-2D|ADR-035]]. Sustituye el flujo síncrono y modal de ADR-035 para que
> las rutas largas no congelen la interacción ni la inspección del mapa.

## Contexto

ADR-035 dejó una preview 2D funcional: se eligen A y B, el planner calcula un `RoutePlan` y el
autor puede recorrer políticas antes de seleccionar. Pero el cálculo es **síncrono** y la preview
**captura todas las teclas**. Para rutas de autoría largas eso tiene dos problemas:

1. A* puede tardar segundos o más, sin feedback ni forma de cancelar.
2. Mientras se inspecciona el plan, el usuario no puede mover el cursor, cambiar la página, hacer
   zoom ni usar la consola de navegación.

El dueño quiere que la espera sea visible y que, una vez hay plan, el mapa vuelva a ser navegable
sin perder la preview. También quiere poder automatizar la operación como comando `connect` en una
fase posterior, reutilizando el mismo planner y la misma cola de construcción.

## Decisión

### Estados

La preview pasa a tener estos estados:

- `PICK_START`: esperando el primer extremo.
- `PICK_END`: primer extremo fijado; se mueve el cursor para elegir el segundo.
- `PLANNING`: hay un job de planificación en curso. Solo `Esc` cancela.
- `CHOOSING`: hay un plan (factible o no) y el mapa vuelve a ser navegable.
- `SELECTED`: plan factible aceptado; queda disponible para la futura cola de construcción.
- `CANCELLED`: preview descartada.

`PICK_START` y `PICK_END` conservan el comportamiento actual. `PLANNING` es nuevo y `CHOOSING`
sustituye al antiguo `PREVIEW` como estado navegable.

### Planificación asíncrona

- Nuevo `RoutePlanningJob` en `core`, UI-free, con un `ExecutorService` de un solo worker.
- `RoutePlanner.plan(...)` gana una sobrecarga con:
  - `ProgressListener`: fase (`CLEARANCE` / `FALLBACK`), nodos expandidos, tiempo transcurrido y
    posición actual opcional.
  - `BooleanSupplier` de cancelación.
- La sobrecarga antigua delega en la nueva con un listener vacío y cancelación falsa.
- El job publica snapshots inmutables; la UI los consulta desde el hilo principal. El worker nunca
  toca `Model`, `View`, `EconomyManager`, `GroundMap` ni el `RailMap`.
- `cancel()` activa el flag y el planner lo comprueba cada N expansiones. La preview no construye
  nada, así que cancelar no deja rastro en el mundo.
- Los resultados se cachean por `RoutePolicy`: volver a una política ya calculada es inmediato.
- Se mantiene **una ruta por política**. No se implementan k-rutas ni varias alternativas dentro de
  la misma política en esta fase.

### Progreso

Mientras `PLANNING`, la barra de estado muestra una barra **indeterminada/animada**, fase, número
de nodos y tiempo:

```text
Connect · PLANNING · CLEARANCE · 234k nodes · 3.2 s · [████░░] · Esc cancel
```

No se inventa un porcentaje: A* no conoce el total de nodos que explorará. La barra es una señal de
actividad, no una medida falsa. No se pinta una ruta provisional; el dueño prefiere probar primero
solo la barra y decidir después si quiere una previsualización parcial.

### Interacción en `CHOOSING`

Con un plan calculado, el mapa vuelve a ser navegable. La preview deja de capturar las teclas de
navegación:

- Cursor: flechas, `hjkl`.
- Página: `PgUp`/`PgDn` y el resto de la navegación de mapa existente.
- Zoom/cámara 2D: `z`/`Z` y las teclas actuales.
- Consola: `:` abre el modo comando.
- 3D: cuando llegue, la orientación de cámara tendrá sus propias teclas; la selección de ruta no
  las tocará.
- El plan y los extremos A/B quedan fijos; navegar no replanifica.

Teclas de ruta, únicas y fuera de la navegación:

- `[` / `]`: política/ruta anterior y siguiente.
- `Enter`: seleccionar el plan actual.
- `Esc`: cancelar la preview.
- Si la política pedida no está cacheada, `[`/`]` vuelve a `PLANNING`; si está, se muestra al
  instante.

`h`/`l` **no** cambian política: son movimiento de cursor, igual que las flechas. Esto revierte la
decisión anterior de la preview síncrona, incompatible con la navegación libre.

### Consola en `CHOOSING`

Mientras hay plan, `:` abre la consola. El plan y la sesión se conservan; al salir se vuelve a
`CHOOSING`. Solo se permiten comandos de **navegación/consulta** que no editan el mundo:

- Permitidos: `go`, `face`, `mark`, `info`, `ls` y equivalentes de navegación.
- Bloqueados: `new`, `del`, `clear`, `slide`, `turtle`, `undo`, `redo`, `load`, `new game`, `quit`
  y cualquier otro que pueda cambiar terreno, vías, economía o endpoints.
- Un comando bloqueado muestra un aviso y no cancela la preview.
- Los comandos permitidos no invalidan el plan; `go` solo mueve el cursor.

En una fase posterior, el comando `connect` automatizará planificar + encolar la construcción y
podrá reutilizar este mismo `RoutePlanningJob` y la cola de obra.

### Construcción

Esta ADR no construye nada. `SELECTED` sigue dejando el plan en `pendingRoutePlan` para la futura
cola de construcción. La automatización `connect` y el `wait` para escenarios quedan fuera de
alcance de esta fase.

## Consecuencias

- La UI sigue repintando durante el cálculo; el progreso es visible y cancelable.
- El estado de planificación vive en core y puede reutilizarse en 3D y en el comando `connect`.
- La navegación libre en `CHOOSING` hace la inspección de rutas largas usable.
- Las teclas de ruta son estables e independientes de la navegación.
- El coste de implementación está en separar el estado de preview del planner y en el key routing;
  el planner puro no cambia de API semántica.

## Fuera de alcance

- Ruta provisional o pintado del frente de búsqueda.
- Múltiples rutas alternativas por política.
- Cola de construcción, materialización y cancelación de obra.
- Comando DSL `connect` y `wait`.
- Controles de cámara 3D.
