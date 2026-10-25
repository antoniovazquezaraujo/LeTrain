# ADR-035: Preview 2D de rutas para autoría de escenarios

## Estado: PROPUESTO

> Borrador de diseño para revisar antes de implementar. No hay código de UI todavía.
> Depende de [[ADR-034-Route-Planner-Cost-Aware|ADR-034]].

## Contexto

ADR-034 deja un planificador de rutas puro: dados dos puntos, el terreno y la economía,
devuelve un `RoutePlan` con pasos, coste y tiempo de construcción. Falta la parte que convierte
eso en una herramienta usable: elegir los extremos, ver una ruta sobre el mapa, comparar políticas
y decidir si se acepta o se descarta.

El dueño ha pedido explícitamente:

- marcar dos puntos y pedir una conexión;
- ver **una** ruta cada vez, no cinco superpuestas;
- conocer coste y tiempo antes de aceptar;
- poder descartar y probar otra política;
- que la preview no construya nada todavía.

La construcción diferida, la cancelación y el cursor único de obra se tratarán en otra rama.

## Decisión propuesta

### Modo `CONNECT` en 2D

Se añade un modo modal al cliente de terminal. En 3D se hará más adelante con el mismo estado.

- Se entra con **`C` mayúscula** (`Shift+C`). La `c` minúscula sigue siendo `LINK`.
- Solo se permite desde un modo de mapa/edición normal; no desde `COMMAND`, `PROGRAM` ni el editor.
- Mientras dura el modo, el cursor se mueve como siempre, pero no se construyen vías ni se cambia
  de modo con los atajos normales.
- `Esc` cancela y vuelve al modo anterior.

### Estados

1. `PICK_START`
   - El cursor está libre.
   - `Enter` fija el punto `A` si la celda es válida.
   - `Esc` cancela.
2. `PICK_END`
   - El cursor está libre; `A` queda marcado.
   - `Enter` fija `B` y pide el primer plan.
   - Si `B` no es válido, se muestra el motivo y se sigue en `PICK_END`.
   - `Esc` cancela directamente el modo.
3. `PREVIEW`
   - Se muestra **un solo** `RoutePlan`.
   - `←`/`→` y `h`/`l` cambian de política y recalculan. En `PICK_START`/`PICK_END`, `h`/`l`
     conservan su comportamiento actual de giro del cursor.
   - `Enter` acepta el plan y pasa a `SELECTED`.
   - `Esc` descarta y cancela.
   - Si la política no tiene ruta, se muestra el motivo y no se permite aceptar.
4. `SELECTED`
   - Se guarda el plan como `pendingRoutePlan` en el presenter.
   - Se cierra el modo y se vuelve al mapa.
   - **No se construye nada**: la cola de obra es otra fase.

### Políticas

Orden de ciclo:

1. `CHEAPEST` (por defecto)
2. `DIRECT`
3. `AVOID_MOUNTAINS`
4. `AVOID_WATER`
5. `AVOID_BOTH`

La política activa y su descripción corta se muestran en la barra de estado. No se muestran varias
rutas a la vez.

### Vista del mapa

- `A` y `B` marcan los extremos.
- La ruta se pinta encima del terreno con este estilo:

  | Celda | Glifo | Frente | Fondo |
  | --- | --- | --- | --- |
  | Vía normal | `═` | cian brillante | terreno (se conserva) |
  | Puente | `≈` | rojo brillante | blanco (opaco) |
  | Túnel | `⋂` (el mismo glifo que la entrada de túnel del renderer) | rojo brillante | blanco (opaco) |
  | Extremo `A`/`B` | `A` / `B` | amarillo brillante | terreno (se conserva) |

- Las celdas normales y los extremos conservan el fondo del terreno: solo cambian glifo y frente.
  Puentes y túneles usan fondo blanco opaco porque ningún color de frente se lee bien a la vez
  sobre roca y agua.
- El extremo gana si comparte celda con un paso de la ruta.
- Solo se pintan las celdas visibles; si la ruta sale de pantalla, se indica en la barra de estado.
- El resto del mapa sigue visible; no se oscurece ni se sustituye.

### Barra de estado

En `PREVIEW`:

```text
Connect · CHEAPEST · $1.234.500 · 1 h 12 min · 87 tramos
←/→/h/l política   Enter seleccionar   Esc descartar
```

Si no hay ruta:

```text
Connect · AVOID_BOTH · sin ruta: no se puede cruzar agua ni montaña
←/→/h/l política   Esc descartar
```

- El coste es la moneda del juego; el tiempo, tiempo de juego.
- Los ticks del planner se convierten a minutos/horas para mostrarlos.
- La construcción automática usa un **tiempo base por celda** además de los delays de puente/túnel.
  Propuesta inicial: **1 tick por celda** (3 segundos de juego; 20 celdas = 1 minuto). Se sumará a
  los delays actuales y será configurable más adelante en `letrain.cfg`
  (`route.construction.baseDelayTicks`, nombre provisional).
- La vía normal manual sigue con `delay.NORMAL_TRACK=0`; el tiempo base es propio de la
  construcción automática de rutas, no cambia la construcción manual.

### Aceptar y descartar

- `Enter` guarda el plan y cierra la preview. No muta el modelo, no gasta dinero y no crea vías.
- `Esc` descarta el plan y cierra la preview.
- El `pendingRoutePlan` queda disponible para la futura cola de construcción, pero esta rama no la
  implementa.
- Confirmado por el dueño: un solo `Enter` basta; no hay segundo paso de confirmación.

## Fuera de alcance de esta rama

- Construcción, cola de obra, cancelación o gasto de dinero.
- Cursor único de obra y bloqueo de construcción manual durante la obra.
- 3D y su overlay.
- Marcas persistentes con nombre; de momento `A`/`B` y coordenadas.
- Estaciones existentes como extremos.
- Varias rutas pintadas a la vez o comparador lado a lado.
- Reutilización de vía existente o forks.
- Guardar/cargar el estado de preview.

## Consecuencias

- La preview es una consulta pura: no puede romper una partida ni gastar dinero.
- El estado nuevo vive en el presenter 2D; no contamina el `Model`.
- El overlay necesita un pequeño gancho de render en `TerminalView`/`RenderVisitor`.
- La rama queda pequeña y se puede revisar sin la cola de obra.
- Si el flujo gusta, la siguiente rama materializa `pendingRoutePlan` con la cola de construcción.

## Decisiones de la revisión del dueño (2026-10-25)

1. **Tecla de entrada**: `Shift+C`; la `c` minúscula sigue siendo link.
2. **Política inicial**: `CHEAPEST`.
3. **Extremos**: cursor + `Enter`; de momento no se seleccionan estaciones ni nombres existentes.
4. **Tiempo**: la construcción automática es más lenta que la manual. Se añade un tiempo base por
   celda (propuesta inicial: 1 tick/celda, configurable más adelante). Los delays de puente/túnel se
   suman encima.
5. **Confirmación**: un solo `Enter` selecciona y cierra; no hay segundo paso.
6. **Resumen**: la barra de estado es suficiente; no hace falta panel aparte.
7. **Cancelar**: `Esc` en `PICK_END` cancela directamente el modo.

### Pendiente de implementación

- Nombre definitivo de la clave de configuración del tiempo base
  (`route.construction.baseDelayTicks` es provisional).
- API exacta para pasar el tiempo base al `RoutePlanner`: parámetro adicional en `plan(...)` o un
  `RoutePlanningOptions`; el planner seguirá siendo puro y read-only.
- El tiempo base se aplicará a todas las celdas del plan, incluidas las de los extremos.
