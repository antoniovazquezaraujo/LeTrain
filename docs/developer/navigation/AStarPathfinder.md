[[Index|⬅️ Volver al Índice]]

# Navegación Autónoma (A* y AutoPilot)

> **ESTADO:** **Implementado y en producción.**  
> El sistema de navegación autónoma basado en el algoritmo A* (`AStarPathfinder`) y el piloto automático (`AutoPilot`) está plenamente integrado en el módulo `core` (`letrain.itinerary`).

## Visión General
El sistema permite que los trenes calculen y recorran de forma autónoma la ruta óptima entre destinos o waypoints (estaciones, sensores), configurando reactivamente los desvíos (`ForkRailTrack`) a lo largo del trayecto y colaborando con el sistema de cantones (`TrainSafetyManager`).

## Componentes Principales
- **`AStarPathfinder`** (`letrain.itinerary.AStarPathfinder`): Implementa la búsqueda A* sobre el grafo topológico ferroviario (`RailwayGraph`) para encontrar la secuencia de segmentos más corta entre dos puntos.
- **`AutoPilot` / `AutoPilotImpl`** (`letrain.itinerary.AutoPilot`): Máquina de estados reactiva y desacoplada de la física (ADR-012, ADR-014) que orienta agujas (`ensureForkRoute`) ante transiciones de segmento.
- **`Itinerary`** (`letrain.itinerary.Itinerary`): Estructura de datos que almacena la lista de `Waypoint`s y comandos de parada, velocidad, espera o inversión.
- **`TrainActionManager`** (`letrain.itinerary.TrainActionManager`): Gestiona la llegada a waypoints y la ejecución programada de acciones.

## Integración con el Lenguaje DSL (ANTLR4)
La automatización permite definir y asignar itinerarios mediante scripts o consola (ver [[../systems/CommandPattern|Gestión de Automatización]] y la documentación de usuario `grammar.md`):
```letrain
create itinerary "RutaCarbon" {
    add station 1 load
    add station 2 unload
}
assign itinerary "RutaCarbon" to train 1;
train 1 set autopilot true;
```

## Enlaces Relacionados
- [[../architecture/AutoPilotAnalysis|Análisis Detallado del AutoPilot]]
- [[../adr/ADR-008-Itinerary-Redesigned|ADR-008: Rediseño del Itinerario]]
- [[../adr/ADR-012-AutoPilot-Simplification|ADR-012: Simplificación del AutoPilot]]
- [[../adr/ADR-014-SafetyManager-Autopilot-Decoupling|ADR-014: Desacoplamiento de SafetyManager y AutoPilot]]

