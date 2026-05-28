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
- **Sistema de Colisiones**:
    - Si un tren contacta con otro a una velocidad `v >= 5`, se dispara el método `crash()`, resultando en la destrucción mutua de los trenes.
    - Si `v < 5`, se produce un contacto de seguridad (`notifyContact()`) y el tren se detiene inmediatamente (`stalled = true`, `speed = 0`).
    - **Detección inmediata post-movimiento**: Tras un movimiento exitoso, `moveLinkers()` verifica si la siguiente celda está ocupada por otro tren. Si es así, dispara la colisión sin esperar al siguiente ciclo de `advance()`, eliminando el retardo de hasta 50 ticks en la detección y el sonido de choque. (Ver [ADR-007](../adr/ADR-007-Collision-Visual-Interpolation.md))
- **Recuperación tras colisión**: Para reanudar la marcha, el jugador debe invertir la dirección (`toggleReversed()`), lo cual limpia el flag `stalled`. `incSpeed()` está bloqueado mientras el tren está stalled.

## Dependencias
- `letrain.track.rail.RailTrack`: Proporciona la conectividad y el estado de ocupación.
- `letrain.vehicle.Tractor`: Interfaz que define la potencia de tracción de la locomotora.
