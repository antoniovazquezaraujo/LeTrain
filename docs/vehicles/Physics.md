# Física de Vehículos (Movimiento y Colisiones)

El motor de movimiento de LeTrain gestiona el avance de los trenes mediante una lógica de simulación basada en **turnos de motor** y una física de avance en dos fases.

## Símbolos Clave
- `letrain.vehicle.impl.rail.Train`: Entidad que agrupa locomotoras y vagones (`Linkers`).
- `letrain.vehicle.impl.rail.Locomotive`: Motor del tren, responsable de consumir los turnos de simulación.
- `Train#moveLinkers(boolean)`: Lógica principal de desplazamiento.
- `Locomotive#updateInertia()`: Gestor de aceleración y frenado gradual.

## Invariantes y Lógica de Movimiento
- **Simulación en 2 Fases**:
    1.  **Fase 1 (Pre-chequeo)**: El tren verifica si la vía destino está físicamente conectada y libre.
    2.  **Fase 2 (Ejecución)**: El tren desplaza físicamente todos sus `Linkers` y actualiza la ocupación de vías.
- **Timing (Turnos de Motor)**: La velocidad del tren determina cuántos ticks de simulación debe esperar antes de moverse. La fórmula de espera es: `turnos = (10 - velocidad_actual) + 1`. 
    - A velocidad máxima (10), el tren se mueve en cada tick (1 turno de espera).
    - A velocidad mínima (1), el tren espera 10 ticks entre movimientos.
- **Inercia**: La velocidad no cambia instantáneamente. La `Locomotive` ajusta su `currentSpeed` hacia su `targetSpeed` de forma gradual en el método `updateInertia()`.
- **Sistema de Colisiones**:
    - Si un tren contacta con otro a una velocidad `v >= 5`, se dispara el método `crash()`, resultando en la destrucción mutua de los trenes.
    - Si `v < 5`, se produce un contacto de seguridad y el tren se detiene inmediatamente.

## Dependencias
- `letrain.track.rail.RailTrack`: Proporciona la conectividad y el estado de ocupación.
- `letrain.vehicle.Tractor`: Interfaz que define la potencia de tracción de la locomotora.
