# Jerarquía de Vías e Infraestructura

El sistema de infraestructura de LeTrain se basa en una jerarquía de clases que permite representar desde un tramo recto simple hasta desvíos complejos, puentes y túneles.

## Jerarquía de Clases
- **`letrain.track.Track` (Abstracta)**: Clase base que define el contrato fundamental de una pieza de infraestructura.
    - **`letrain.track.rail.RailTrack`**: Especialización para vías férreas estándar.
        - **`letrain.track.rail.ForkRailTrack`**: Representa un desvío (fork). Implementa `DynamicRouter` para gestionar cambios de aguja.
        - **`letrain.track.rail.BridgeRailTrack`**: Extensión para tramos de puente.
        - **`letrain.track.rail.TunnelRailTrack`**: Extensión para tramos de túnel.
        - **`letrain.track.rail.StationRailTrack`**: Vía que contiene una estación.

## Mecanismos de Navegación
La navegación dentro de una pieza de vía no es directa, sino que se delega en componentes especializados:
1. **`letrain.track.Router`**: Interfaz que determina la salida de una vía dada una entrada.
    - `SimpleRouter`: Para vías rectas o curvas con una única entrada y salida.
    - `ForkRouter`: Para desvíos, donde la salida depende del estado de la aguja.
2. **`letrain.track.TrackDirector`**: Clase de utilidad que coordina el movimiento de un `Linker` (locomotora o vagón) a través de una cadena de vías, gestionando las conexiones (`Connectable`).

## Símbolos Clave para Desarrolladores
- `Track#getConnectors()`: Devuelve los puntos de conexión física de la vía.
- `DynamicRouter#toggle()`: Cambia el estado de un desvío (Fork).
- `TrackFormat`: Enum que define la geometría visual y lógica de la vía (RECTA, CURVA, etc.).

## Invariantes
- Una vía solo puede conectarse a otra si sus conectores son compatibles espacialmente.
- Los `ForkRailTrack` deben tener siempre un `ForkEventListener` asociado si forman parte de un itinerario automático.
