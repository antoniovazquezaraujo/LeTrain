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

## Geometría y Restricciones
LeTrain utiliza una rejilla octogonal para las direcciones (`Dir`), permitiendo movimientos en 8 direcciones (N, NE, E, SE, S, SW, W, NW).

1. **La Regla de los 45 Grados**: No se permiten curvas internas que superen los 45 grados en una sola pieza. Una curva siempre conecta una dirección cardinal con su diagonal adyacente (ej: N <-> NW).
2. **Conexiones y "Quiebros" (Kinks)**: El sistema permite conectar piezas cuyas salidas y entradas no están perfectamente alineadas a 180º (ej: una salida NW conectada a una entrada S). Aunque estas conexiones crean un "quiebro" visual y lógico, el sistema de navegación (`RailIterator`) y movimiento (`TrackDirector`) están diseñados para tolerar estas inconsistencias manteniendo la dirección de marcha si no se encuentra un puerto exacto.

## Símbolos Clave para Desarrolladores
- `Track#getConnectors()`: Devuelve los puntos de conexión física de la vía.
- `DynamicRouter#toggle()`: Cambia el estado de un desvío (Fork).
- `TrackFormat`: Enum que define la geometría visual y lógica de la vía (RECTA, CURVA, etc.).

## Invariantes
- Una vía solo puede conectarse a otra si sus conectores son compatibles espacialmente.
- Los `ForkRailTrack` deben tener siempre un `ForkEventListener` asociado si forman parte de un itinerario automático.

## Movimiento de Elementos de Vía (issue #468)
`Model.moveSensor(Sensor, Dir)`, `moveSensorForward(Sensor)` y `moveSensorBackward(Sensor)` desplazan un elemento (sensor, estación, señal de velocidad o semáforo) una celda de reposo a lo largo de la vía (operación de edición del usuario; nunca se ejecuta dentro de loops de tick ni reservas de bloque). Semántica del escaneo (`Model.findMoveDestination`, equivalente a `RailIterator.advance`):
- Desde la celda origen se avanza con `getConnected(heading)`; el puerto de entrada a la celda candidata es `heading.inverse()` y la salida se obtiene con `getDir(port)`.
- Una celda con otro `TrackComponent` se **salta**; se sigue buscando la primera celda libre en esa dirección.
- Una celda con un tren (`getLinker() != null`) **aborta** el movimiento: nunca se salta por encima de trenes.
- Un `ForkRailTrack` es un nodo de ruteo, nunca celda de reposo: se atraviesa siguiendo la rama activa (`isUsingAlternativeRoute()`).
- Al mover una `Station`, `applyStationRoleByIndustry(Station, Point)` re-evalúa su rol industrial (radio 5) con la misma lógica usada en la creación ("espejo de la creación"). Los elementos conservan identidad (`id`) y `creationDir`; las listas del `Model` y el componente del `Track` se mantienen sincronizados sin re-registrar el elemento.

`RailSemaphore extends Sensor` (issue #470): el semáforo hereda `Track`/posición derivada/orientación de `Sensor` y se mueve con las mismas `moveSensor*`. El movimiento orientado usa la `creationDir` del propio elemento como sentido de avance, no el cursor: el elemento debe "descansar" siempre con su orientación alineada a un extremo real de la vía. Al avanzar, `creationDir` se rota a la dirección de salida del tramo (`continuationDir`), de modo que el elemento sigue la vía en curvas; al retroceder se usa el extremo opuesto (`backEndDir`) y la orientación apunta de vuelta al origen. De esta forma el elemento ya no queda bloqueado al llegar a una curva o cruzar un desvío que gira.
