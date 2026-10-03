# Física de Vehículos (Movimiento y Colisiones)

El motor de movimiento de LeTrain gestiona el avance de los trenes mediante una lógica de simulación basada en **turnos de motor** y una física de avance en dos fases.

## Símbolos Clave
- `letrain.vehicle.rail.impl.Train`: Entidad que agrupa locomotoras y vagones (`Linkers`).
- `letrain.vehicle.rail.impl.Locomotive`: Motor del tren, responsable de consumir los turnos de simulación.
- `Train#moveLinkers(boolean)`: Lógica principal de desplazamiento.
- `Locomotive#updateInertia()`: Gestor de aceleración y frenado gradual.

## Invariantes y Lógica de Movimiento
- **Simulación en 2 Fases**:
    1.  **Fase 1 (Pre-chequeo)**: El tren verifica si la vía destino está físicamente conectada y libre. Si el destino está ocupado por otro tren, se aborta el movimiento sin desplazar ningún `Linker`.
    2.  **Fase 2 (Ejecución)**: El tren desplaza físicamente todos sus `Linkers` y actualiza la ocupación de vías.
- **Timing (Turnos de Motor)**: La velocidad del tren determina cuántos ticks de simulación debe esperar antes de moverse. La fórmula es: `turns = 50 / currentSpeed` (con `currentSpeed > 0`). Si `currentSpeed == 0`, `turns = -1` (sin movimiento).
    - A velocidad 10, el tren espera 5 ticks entre movimientos.
    - A velocidad 1, el tren espera 50 ticks (~2.5s a 20 TPS).
- **Inercia**: La velocidad no cambia instantáneamente. La `Locomotive` ajusta su `currentSpeed` hacia su `targetSpeed` de forma gradual en el método `updateInertia()`.
    - **Arranque 0→1 (desde parado)**: no es instantáneo. Con el tren parado no hay cadencia de raíles, así que la rama de arranque cuenta **ticks** y exige `Locomotive.START_STEP_TICKS = 100` ticks (~5 s a 20 TPS): lo mismo que un tramo completo en marcha. El tick que engancha la marcha 1 no consume turno de movimiento, de modo que el paso 0→1 y los pasos en marcha quedan exactamente a 100 ticks (0→1 en el 100, 1→2 en el 200, …). `START_STEP_TICKS` es la única constante que se toca para afinar el arranque por oído (junto con la transición de audio `trans-0-1`).
- **Sistema de Colisiones**:
    - Si un tren contacta con otro a una velocidad `v >= 5`, se dispara el método `crash()`, resultando en la destrucción mutua de los trenes.
    - Si `v < 5`, se produce un contacto de seguridad (`notifyContact()`) y el tren se detiene inmediatamente (`stalled = true`, `speed = 0`).
    - **Detección inmediata post-movimiento**: Tras un movimiento exitoso, `moveLinkers()` verifica si la siguiente celda está ocupada por otro tren. Si es así, dispara la colisión sin esperar al siguiente ciclo de `advance()`, eliminando el retardo de hasta 50 ticks en la detección y el sonido de choque. (Ver [ADR-007](../adr/ADR-007-Collision-Visual-Interpolation.md))
- **Recuperación tras colisión**: Para reanudar la marcha, el jugador debe invertir la dirección (`toggleReversed()`), lo cual limpia el flag `stalled`. `incSpeed()` está bloqueado mientras el tren está stalled.

### Tiempos por paso de marcha (20 TPS)

| Paso | Coste | Duración |
|---|---|---|
| Arranque 0→1 (desde parado) | `START_STEP_TICKS` ticks | 100 ticks (5 s) |
| Marcha v→v+1 | `v * 2` raíles × `50 / v` ticks | 100 ticks (5 s) |
| Frenada v→v−1 | `v` raíles × `50 / v` ticks | 50 ticks (2,5 s) |

## Dependencias
- `letrain.track.rail.RailTrack`: Proporciona la conectividad y el estado de ocupación.
- `letrain.vehicle.Tractor`: Interfaz que define la potencia de tracción de la locomotora.

## Descarrilamiento por curvas/desvíos (issue #350, ADR-019)

- **Unidad de tiempo**: el reloj de la simulación es el **tick de motor** (20 TPS; ver
  `WaypointCommand.TICKS_PER_SECOND` y `docs/developer/architecture/GameLoop.md`). El tren cruza una
  casilla cada `50 / currentSpeed` ticks, así que el instante de la última curva codifica la
  velocidad efectiva (frenadas/aceleraciones incluidas).
- **Reloj por tren**: `Train#advanceSimulationTick()` avanza una vez por tick (invocado desde
  `Locomotive.update()` solo para la locomotora directora). El historial de la última curva vive en
  `TrainSafetyManager` (un único `long lastCurveTick`, -1 = sin historial).
- **Regla**: al entrar la cabeza en una pieza curva (rumbo de salida ≠ rumbo de entrada) con
  velocidad actual ≥ `derail.minSpeed`, si han pasado menos de `derail.minCurveInterval` ticks desde
  la última curva el tren **descarrila** (`Train#crashDestroy`, mismo pipeline que una colisión).
- **Desvíos**: no son un caso especial. Un `ForkRailTrack` recorrido en recto es una recta más; un
  desvío desviado es una curva normal (cuenta para el intervalo). El peligro es el cambio de rumbo,
  no el objeto "desvío".
- **Reset**: el historial se limpia cuando el tren se detiene por completo o invierte la marcha.
- Parámetros en `letrain.cfg`: `derail.minCurveInterval`, `derail.minSpeed`. La evaluación
  ocurre en `TrainMovementManager` (Fase 1A), antes de mover o reservar ningún linker.
