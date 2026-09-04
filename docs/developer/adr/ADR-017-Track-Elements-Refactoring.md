# ADR-017: Refactorización de Elementos de Vía (Track Elements)

## Contexto
Actualmente, la arquitectura de la clase `RailTrack` y los elementos que pueden existir en ella (como `Sensor`, `RailSemaphore`, `SpeedSignal`, `Station`, y `ForkRailTrack`) está muy acoplada. Esto genera varios problemas ("está todo bastante liado"):
- Gran cantidad de validaciones `instanceof`.
- Responsabilidades mezcladas (lógica de negocio vs renderizado vs reglas de colisión).
- Difícil de extender si queremos añadir nuevos elementos (ej. pasos a nivel, desvíos triples).
- Posible abuso de la herencia en lugar de la composición.

## Diagnóstico Actual (Fase 1 completada)
El análisis exhaustivo del código actual ha revelado los siguientes problemas arquitectónicos:

### 1. Problemas de Herencia y Composición
- **Track como "God Object"**: La clase abstracta `Track` alberga directamente campos para elementos opcionales (`private Sensor sensor`, `private RailSemaphore semaphore`). En lugar de usar componentes genéricos, tiene "slots" fijos en memoria que estarán a `null` en el 90% de las vías.
- **Herencia forzada de Sensores**: `Station` y `SpeedSignal` heredan de `Sensor` para poder ocupar el "slot" de sensor de una vía, mezclando conceptos semánticos distintos.
- **Identidad duplicada (Code Smell)**: Coexisten `Station` (hereda de `Sensor`) y `StationRailTrack` (hereda de `RailTrack`). No queda claro si una estación es un "sensor sobre la vía" o un "tipo especial de vía".
- **Herencia por motivos puramente visuales**: `BridgeRailTrack` y `TunnelRailTrack` heredan de `RailTrack` única y exclusivamente para sobrescribir `accept(Visitor)` de cara al renderizado, sin añadir ninguna lógica mecánica real.

### 2. Acoplamiento y Abuso de Tipos
- **Dependencias Circulares y SRP**: `Track` gestiona Routing, Mapeo, Física (TrackDirector) e Infraestructura, conociendo clases detalladas como `RailSemaphore`.
- **Jackson y OCP**: `Track` tiene codificados estáticamente a todos sus hijos (`@JsonSubTypes({BridgeRailTrack, TunnelRailTrack...})`), rompiendo el Principio Abierto-Cerrado.
- **Abuso de `instanceof`**: 
  - `instanceof ForkRailTrack` aparece 19 veces en la lógica del motor (ej. `AutoPilot`, `TrainMovementManager`) en lugar de interactuar polimórficamente con el `Router`.
  - `instanceof Station` aparece 12 veces (lógica de logística y renderizado) en lugar de resolverlo mediante polimorfismo (ej. un `onVehicleEntered()`).

## Propuestas de Arquitectura (Fase 2)
*(Pendiente de debate con el equipo. Alternativas a evaluar: Decorator Pattern vs Entity-Component-System para los elementos de vía, y separación estricta Modelo/Vista para evitar subclases como BridgeRailTrack)*.
