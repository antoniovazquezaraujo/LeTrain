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

- `RoutePolicy` (`DIRECT`, `AVOID_MOUNTAINS`, `AVOID_WATER`, `AVOID_ALL`, `CHEAPEST`).
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
- `AVOID_MOUNTAINS`: bloquea solo `ROCK`; el agua (puente) está permitida. Minimiza distancia.
- `AVOID_WATER`: bloquea solo `WATER`; la roca (túnel) está permitida. Minimiza distancia.
- `AVOID_ALL`: bloquea todos los terrenos evitables (hoy `WATER` y `ROCK`); no bloquea suelo ni
  industrias, y los extremos siguen siendo construibles. Minimiza distancia.
- `CHEAPEST`: sin bloqueo; minimiza el coste real de construcción.

Las políticas `AVOID_*` son **literales**: evitan únicamente el terreno que nombran y optimizan
**distancia geométrica**. El terreno permitido (agua bajo `AVOID_MOUNTAINS`, roca bajo
`AVOID_WATER`) no se evita por ser caro: si el puente/túnel es el atajo geométrico, se cruza y el
plan reporta su coste y tiempo reales. `CHEAPEST` es la única política que minimiza el coste de
construcción. Si el destino no es alcanzable se devuelve `infeasible` con un motivo que nombra la
política y los puntos.

### Curvatura máxima: 45° por paso

La ruta planificada debe poder construirse con `RailTrackMaker`, que rechaza cualquier cambio de
rumbo cuyo valor absoluto supere un paso angular (`Math.abs(oldDir.inverse().angularDistance(dir))
> 1`, es decir, 90°, 135° o 180°). Por eso el estado de A* **no es solo la celda**: incluye también
la dirección con la que se llegó a ella.

- El primer paso puede elegir cualquiera de las 8 direcciones.
- Cada paso siguiente exige `Math.abs(previous.angularDistance(next)) <= 1`.
- Se rechazan por tanto los giros de 90°, 135° y 180°; un giro neto de 90° solo se consigue con dos
  pasos de 45° (por ejemplo E → NE → N).
- La regla diagonal existente (no recortar esquinas entre dos ortogonales bloqueados) se mantiene.
- Consecuencia: un desvío alrededor de un obstáculo puede necesitar más celdas que la distancia
  geométrica mínima, porque hay que "girar" gradualmente. Los tests fijan las rutas esperadas.

### Coste de búsqueda: comodidad en `AVOID_*`

El coste primario en `AVOID_MOUNTAINS`, `AVOID_WATER` y `AVOID_ALL` es la **distancia
geométrica**. Sobre él se suma, **solo al coste de búsqueda**, un término de comodidad determinista
expresado en las mismas **unidades de distancia**:

```
coste_búsqueda = distancia_geométrica(paso)
               + 0,25 × nº_giros_45
               + 0,50 × vecinos_bloqueados(celda)
               + 0,05 × desviación_euclídea(celda)
```

donde:

- `vecinos_bloqueados`: cuántos de los 8 vecinos están bloqueados **según la política activa**
  (falta de datos, terreno no construible y el bloqueo propio de la política). Bajo
  `AVOID_WATER` cuenta el agua; bajo `AVOID_MOUNTAINS`, la roca; bajo `AVOID_ALL`, ambos. Bajo
  `AVOID_MOUNTAINS` el agua permitida **no** cuenta como bloqueada, y bajo `AVOID_WATER` la roca
  permitida tampoco.
- `desviación_euclídea`: distancia de la celda al segmento recto inicio-fin, en celdas.
- `nº_giros_45`: 1 por cada cambio de rumbo de 45°.

Al estar en unidades de distancia, la comodidad solo refina rutas de longitud parecida: una celda
ortogonal extra cuesta 1, un giro 0,25 y la desviación 0,05 por celda. El peso 0,5 de limpieza puede
sumar a lo largo de una costa y separar la vía del borde, pero no convierte en ganadora una ruta
mucho más larga. El dueño prefiere una ruta algo más larga pero cómoda y lejos del terreno
bloqueado antes que la más corta pegada a la costa; es un intercambio intencional, no un empate.

### Paso duro de limpieza (dos pasadas)

Para `AVOID_MOUNTAINS`, `AVOID_WATER` y `AVOID_ALL` la comodidad anterior no basta: una ruta puede
seguir pegada a la costa si esa es la más corta. Por eso el planificador hace **dos pasadas** con
el mismo A*, las mismas reglas de giro/esquina, la misma métrica y el mismo determinismo:

1. **Pasada con limpieza dura (primera).** Además de bloquear el terreno de la política, ninguna
   celda de la ruta puede tener un vecino bloqueado. Es decir, toda celda intermedia debe cumplir
   `vecinos_bloqueados = 0`, quedando al menos a una celda del terreno **nombrado** por la política
   (roca en `AVOID_MOUNTAINS`, agua en `AVOID_WATER`, ambos en `AVOID_ALL`). El terreno permitido
   no exige limpieza dura. **Origen y destino están exentos** (pueden estar en costa o cerca de un
   borde). Si esta pasada encuentra ruta, se devuelve.
2. **Fallback (segunda).** Si la pasada dura es infactible (corredor de una celda, destino
   encerrado, borde del mapa), se repite la búsqueda sin la restricción dura y se devuelve su mejor
   plan. Así los corredores estrechos siguen siendo transitables aunque la vía toque el borde.

El término de comodidad por vecinos bloqueados se mantiene en ambas pasadas: en la primera es
redundante para los vecinos inmediatos (todas las celdas intermedias tienen cero), pero sigue
distinguiendo rutas a dos o tres celdas del obstáculo. En el fallback es lo que evita, dentro de lo
posible, ir pegado a la costa. El fallback es raro y no se registra nada: simplemente se devuelve el
mejor plan posible.

`DIRECT` y `CHEAPEST` **nunca** usan la pasada dura: conservan su semántica primaria y solo usan
desempates deterministas diminutos. En `DIRECT` son `1e-3` por giro y `1e-5` para
`desviación + vecinos_bloqueados` sobre la distancia unitaria; en `CHEAPEST` son esas mismas
fracciones aplicadas al coste primario mínimo por celda. En todos los casos las penalizaciones
**no** se suman a `totalCost()` ni a `totalConstructionTicks()`: el plan reporta solo el coste y
tiempo reales de las celdas elegidas.

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
ambas métricas (octile para distancia; coste mínimo por celda × Chebyshev para coste), y sigue
siendo admisible con las penalizaciones de forma porque estas son no negativas y la heurística las
ignora. Mismos inputs ⇒ misma ruta, en cualquier JVM. Los tests cubren una simetría con dos rutas de
igual coste.

## Decisiones menores tomadas para no expandir el alcance

- **Movimiento diagonal sin recortar esquinas**: una celda está bloqueada si no tiene datos de
  terreno, si su valor no es construible (`GROUND`/`WATER`/`ROCK`) o si la `RoutePolicy` la
  bloquea. Un movimiento diagonal se **rechaza** cuando ambos ortogonales adyacentes están
  bloqueados; si al menos uno es transitable, se permite. Aplica a todas las políticas: en
  `DIRECT`/`CHEAPEST` el agua y la roca son transitables, así que solo bloquean los valores
  ausentes o no construibles. Consecuencia: no se puede cruzar en diagonal por la esquina entre
  dos celdas no construibles; si eso deja el destino encerrado, el plan es infactible con motivo.
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
   (`DIRECT`, `AVOID_MOUNTAINS`, `AVOID_WATER`, `AVOID_ALL` o `CHEAPEST`) o ejecutarlo.
3. Si el plan es factible, la capa de construcción (futura) materializa `steps()`; si no, se
   muestra `reason()` en vez de una ruta vacía.
4. La obra puede cancelarse; lo ya construido queda a medio terminar.

## Fuera de alcance del spike

- Previsualización en UI (2D/3D); se mostrará un plan cada vez, no un comparador de varios.
- Cola de construcción, cancelación, reanudación o guardado/carga a mitad de obra.
- Comando de alto nivel `connect` en el DSL, wiring de escenarios o CLI.
- Puertas (`*_GATE_TRACK`), desvíos, estaciones, sensores o señalización dentro del plan.
- Modelo de pendientes, velocidad o capacidad; la curvatura solo se valida como regla de
  construcción (máximo 45° por paso), no se modela la velocidad por curva. El planificador es
  geométrico/económico, no de explotación.
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
- Al respetar la curvatura real de 45°, algunas rutas son más largas que la distancia geométrica
  mínima (hay que girar gradualmente). Es intencional: el plan debe poder construirse.
- La pasada dura de limpieza hace que las rutas `AVOID_*` dejen al menos una celda libre respecto
  al agua/roca; cuando el terreno no lo permite (corredores), el fallback garantiza que siga
  habiendo un plan transitable aunque toque el borde.
- El planificador no recorta esquinas entre celdas bloqueadas (regla diagonal decidida); la de no
  modelar puertas queda para la capa de construcción.

## Pendiente

- **Celda de origen con vía/estación existente**: solo aplica si en el futuro se permite reutilizar
  vía; de momento el plan construye todas las celdas.
