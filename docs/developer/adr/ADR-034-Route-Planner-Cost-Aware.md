# ADR-034: Planificador de rutas con coste para autoría de escenarios

## Estado: PROPUESTO

> Spike de implementación (core, sin UI). Documenta la primera capa del modelo de autoría de dos
> capas descrito en [[ADR-020-Command-Journal-Scenarios|ADR-020]]. Complementa a
> [[ADR-032-Wait-Stops-The-Train|ADR-032]] y [[ADR-033-Fork-Orientation-Parallel-Loops|ADR-033]]
> en lo relativo a itinerarios y topología, pero no los modifica.

## Contexto

El dueño quiere que autorizar un escenario sea barato: marcar dos puntos lejanos, ver **varias
opciones de ruta con su precio y su tiempo de construcción**, y elegir una. Hoy la construcción se
hace celda a celda con la tortuga (`write`, `go`, `face`) o a mano en el cliente; no existe una
forma de preguntar "¿cuánto me costaría unir A con B, y por dónde?".

La ruta física de un tren ya la resuelve `AStarPathfinder` sobre el grafo de segmentos (ADR-033),
pero eso es navegación en tiempo de ejecución sobre vía **ya construida**. Lo que falta es lo
contrario: planificar **qué celdas construir** antes de construir nada, con el terreno y la
economía como entradas.

## Decisión

### Modelo de autoría en dos capas

1. **Capa de planificación (este spike).** Dado `GroundMap` + `EconomyManager` + dos puntos +
   una `RoutePolicy`, devolver un `RoutePlan` inmutable: pasos ordenados, coste total, ticks
   totales, factibilidad y motivo legible si no hay ruta. Es una consulta pura: **no muta** ni el
   terreno ni la economía ni el `RailMap`, y no encola construcción.
2. **Capa de construcción (fuera de alcance).** Un futuro comando de alto nivel `connect` (o una
   acción de UI) tomará un `RoutePlan` y lo materializará con la maquinaria existente de
   `RailTrackMaker`/economía, de forma diferida y cancelable.

La separación permite mostrar previsualización, comparar alternativas y validar un escenario sin
tocar el mundo. El planificador no conoce presentadores, visores ni el reloj de simulación.

### API

Paquete nuevo `letrain.planner` en `core`:

- `RoutePolicy` (`DIRECT`, `AVOID_MOUNTAINS`, `AVOID_WATER`, `AVOID_BOTH`, `CHEAPEST`).
- `RoutePlanner.plan(GroundMap terrain, EconomyManager economy, Point start, Point end,
  RoutePolicy policy) -> RoutePlan` (método estático puro).
- `RoutePlan`: `List<RouteStep> steps()`, `double totalCost()`,
  `long totalConstructionTicks()`, `boolean feasible()`, `Optional<String> reason()`.
- `RouteStep`: `Point point()` y `Presenter.TrackType trackType()`.

`RouteStep` **no** modela puertas (`BRIDGE_GATE_TRACK`, `TUNNEL_GATE_TRACK`). Las puertas son un
detalle de transición en la construcción (`RailTrackMaker.detectTrackType`), no una decisión de
autoría; el plan solo distingue vía normal, puente y túnel.

### Políticas y métrica

A* sobre la rejilla en 8 direcciones. Las celdas se normalizan como en `RailTrackMaker`: los
valores de industria `10..29` cuentan como `GROUND`. Los extremos deben ser suelo/industria
construible; agua o roca como extremo devuelven un plan infactible con motivo.

- `DIRECT`: sin bloqueo de terreno. Minimiza distancia geométrica (ortogonal 1, diagonal `√2`);
  después reporta el coste y tiempo **reales** de las celdas elegidas.
- `AVOID_MOUNTAINS`: bloquea `ROCK`; el agua (puente) está permitida.
- `AVOID_WATER`: bloquea `WATER`; la roca (túnel) está permitida.
- `AVOID_BOTH`: bloquea `WATER` y `ROCK`.
- `CHEAPEST`: sin bloqueo; minimiza el coste real de construcción.

En las políticas `AVOID_*`, tras bloquear se optimiza el **coste real** de las celdas permitidas,
de modo que la ruta devuelta es la más barata que respeta la restricción. Si el destino no es
alcanzable se devuelve `infeasible` con un motivo que nombra la política y los puntos.

### Coste y tiempo

No se inventan valores económicos. Se leen del `EconomyManager` existente:

- `CONSTRUCTED_NORMAL_RAIL_TRACK`, `CONSTRUCTED_BRIDGE_RAIL_TRACK`,
  `CONSTRUCTED_TUNNEL_RAIL_TRACK` vía `getCost(...)`.
- `getConstructionDelay(type)` para los ticks. Normal vale 0 con la configuración por defecto;
  puente y túnel usan el valor configurado.

El coste de una ruta es la suma del coste de **cada celda** del plan; los ticks, la suma de sus
delays. El plan incluye la celda de origen y la de destino, de modo que el total es lo que costaría
construir la unión completa.

### Determinismo

Los vecinos se expanden en un orden fijo (N, NE, E, SE, S, SW, W, NW) y la cola de abiertos rompe
empates de `f` por secuencia de inserción (FIFO). La heurística es admisible y consistente para
ambas métricas (octile para distancia; coste mínimo por celda × Chebyshev para coste). Mismos
inputs ⇒ misma ruta, en cualquier JVM. Los tests cubren una simetría con dos rutas de igual coste.

## Decisiones menores tomadas para no expandir el alcance

- **Movimiento diagonal y celdas no construibles**: una celda no construible es el agua o la roca
  cuando la política las bloquea, o cualquier valor de terreno fuera de `GROUND`/`WATER`/`ROCK`.
  Hoy se permite pasar en diagonal entre dos celdas bloqueadas (lectura simple de A* en 8
  direcciones). La recomendación es prohibirlo cuando los dos ortogonales estén bloqueados; queda
  pendiente de decisión del dueño.
- **Origen igual a destino** devuelve un plan factible vacío, con coste y ticks 0 (no hay nada que
  construir).
- **Coste por celda**, no por longitud: una celda diagonal cuesta lo mismo que una ortogonal, igual
  que una pieza de vía se paga por celda.
- **Tope de expansión** (`MAX_EXPANDED_NODES = 1_000_000`): el terreno procedural no tiene límites y
  un destino inalcanzable podría expandir sin fin. Al agotarlo se devuelve infactible con motivo;
  no afecta a distancias de autoría razonables.
- **Extremos estrictos**: agua/roca como origen o destino se rechazan. Confirmado por el dueño: no
  se permite empezar ni terminar sobre agua o roca, aunque la política permita cruzar esos terrenos
  por el medio.

## Golden path del dueño

1. El autor marca dos puntos lejanos (`start`, `end`).
2. El sistema calcula un plan para la política elegida y muestra **un solo plan** con su
   `totalCost()` y `totalConstructionTicks()`. El autor puede descartarlo y pedir otra política
   (`DIRECT`, `AVOID_MOUNTAINS`, `AVOID_WATER`, `AVOID_BOTH` o `CHEAPEST`) o ejecutarlo.
3. Si el plan es factible, la capa de construcción (futura) materializa `steps()`; si no, se
   muestra `reason()` en vez de una ruta vacía.
4. La obra puede cancelarse; lo ya construido queda a medio terminar.

## Fuera de alcance del spike

- Previsualización en UI (2D/3D); se mostrará un plan cada vez, no un comparador de varios.
- Cola de construcción, cancelación, reanudación o guardado/carga a mitad de obra.
- Comando de alto nivel `connect` en el DSL, wiring de escenarios o CLI.
- Puertas (`*_GATE_TRACK`), desvíos, estaciones, sensores o señalización dentro del plan.
- Modelo de pendientes, curvas, velocidad o capacidad; el planificador es geométrico/económico,
  no de explotación.
- Reutilizar vía ya construida (`RailMap`) en el cálculo de coste: este spike planifica sobre
  terreno desnudo.

## Decisiones del dueño para la capa de construcción (2026-10-25)

- **Un plan a la vez**: se descarta o se ejecuta; no hay comparador de varios planes.
- **Una sola obra/cursor a la vez**: mientras se construye la ruta, el jugador no puede construir
  otras vías con el cursor.
- **Cancelación**: cancelar detiene la obra; lo ya construido queda a medio terminar.
- **Sin reutilizar vía existente ni crear forks**: la ruta puede atravesar vías existentes en
  diagonal o perpendicular, pero no se acopla a ellas ni crea bifurcaciones.
- **Celda de origen**: como el spike planifica sobre terreno desnudo y no reutiliza vía, el
  `connect` construye todas las celdas del plan, incluida la de origen. La pregunta de saltarse la
  celda de origen solo aplica si en el futuro se reutiliza vía/estación.

## Consecuencias

- La lógica de coste queda centralizada en el planificador y reutiliza la economía real, evitando
  tablas de precios paralelas.
- El core gana un paquete puro y testeable sin UI, con tests rápidos sobre un `GroundMap` de
  mentira y `EconomyManager` mockeado.
- El coste de búsqueda es acotado; rutas muy largas pueden devolver infactible por tope antes que
  por ausencia real de ruta (motivo visible). Si molesta, se sube el tope o se pasa a búsqueda con
  caja envolvente.
- La decisión sobre recortar esquinas sigue pendiente; la de no modelar puertas queda para la
  capa de construcción.

## Pendiente

- **Diagonal entre celdas no construibles**: definir si se prohíbe cuando los dos ortogonales están
  bloqueados. Recomendación: prohibirlo. Una celda no construible es agua/roca bloqueada por la
  política, o un valor de terreno fuera de `GROUND`/`WATER`/`ROCK`.
- **Celda de origen con vía/estación existente**: solo aplica si en el futuro se permite reutilizar
  vía; de momento el plan construye todas las celdas.
