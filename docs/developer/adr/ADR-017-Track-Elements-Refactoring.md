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

## Decisión de Arquitectura: Single Component (Opción A Simplificada)
Se ha decidido adoptar un modelo de composición polimórfica estricto (0..1 componentes por vía), ideal para la representación gráfica en ASCII:
1. **`Track` como Contenedor Único**: La vía deja de tener campos específicos (`sensor`, `semaphore`). En su lugar, contendrá un único campo polimórfico: `private TrackComponent component;`.
2. **Cardinalidad 0..1 garantizada**: Al tener un solo campo, garantizamos estructuralmente que una vía no puede tener un Semáforo y una Estación a la vez, evitando solapamientos visuales (Z-fighting) en el modo ASCII y simplificando la lógica.
3. **Interfaz Polimórfica `TrackComponent`**: Los elementos de la vía (Semáforos, Sensores, Estaciones, etc.) implementarán esta interfaz, que definirá métodos del ciclo de vida como `onTrainEnter()`, `onTrainLeave()`, `onTick()`.
4. **Delegación de Eventos**: Cuando un tren pisa una vía, la vía simplemente verificará `if (component != null) component.onTrainEnter()`, eliminando los `instanceof`.

## Plan de Refactorización (Baby Steps)
Para evitar romper los tests y la estabilidad de la rama `develop`, la migración se hará componente a componente:
1. **PR 1 - Core Framework**: Crear la interfaz `TrackComponent` y añadir el campo `component` en `Track`.
2. **PR 2 - Migración Semáforos**: Convertir `RailSemaphore` en un `TrackComponent` y eliminar el campo `semaphore` de `Track`.
3. **PR 3 - Migración Sensores**: Convertir `Sensor` (y sus derivados `SpeedSignal` y `Station`) en componentes y eliminar el campo `sensor`.
4. **PR 4 - Limpieza de Herencia**: Eliminar `BridgeRailTrack` y `TunnelRailTrack` usando componentes visuales o metadatos de terreno, y unificar el concepto de Station.
5. **PR 5 - Routing Polimórfico**: Refactorizar el motor para no depender de `instanceof ForkRailTrack` usando interfaces como `Routable`.
