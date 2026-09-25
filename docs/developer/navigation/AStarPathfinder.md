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

## Misiones de un solo uso (issue #619)
Además de los itinerarios, el DSL puede ordenar **maniobras de un solo uso**: `stop at station|sensor`,
`stop at end` y `stop when blocked`. La velocidad viaja en la orden y el tren acaba parado; no son un
plan que se repita, sino un trabajo puntual.

- **`TrainMission`** (`letrain.itinerary.TrainMission`): valor con tipo, destino, velocidad y estado
  (`ACTIVE`/`COMPLETED`/`FAILED`/`CANCELLED`). Se ejecuta dentro de `AutoPilotImpl` y es transitorio:
  no se serializa (al cargar una partida sin misión ni itinerario el autopilot vuelve a manual).
- **Planificación**: para estación/sensor se decide primero con un **paseo físico** (`RailIterator`)
  si el destino está delante o detrás (A* es por segmentos y no distingue el sentido dentro de un
  cantón); si solo está detrás, la orden invierte el tren una vez. `stop at end`/`stop when blocked`
  no usan A*: siguen la topología hasta que la vía se acaba (`stop at end`) o manda el bloqueo.
- **Frenada**: la misión camina la vía física hasta el destino y aplica la misma curva que la capa de
  seguridad (issue #633): capa el target a `maxSpeedForRails(vías restantes)` y engancha el freno con
  `setTargetSpeedDirect(0)` cuando `brakingRailsFromCurrentState() > vías restantes`. `stop at end`
  apunta a la vía anterior al tope (sin contacto); estación/sensor paran encima del componente.
- **Seguridad**: la curva de la misión solo **baja** el target; cuando el bloque siguiente no se puede
  reservar manda el plan de frontera del `TrainSafetyManager` (el tren rueda hasta la última vía de su
  cantón). `stop when blocked` completa **parado en esa frontera** (se evalúa en `onTick`, porque tras
  el último avance ya no hay hook de vía); si el bloque se libera mientras rueda, no estaba bloqueado y
  sigue hasta el siguiente. Un bloqueo en una misión `stop at sensor` no la cancela: espera y reanuda
  al liberarse. Una orden nueva descarta la espera anterior al arrancar (`cancelBlockWait`) y rehace el
  cálculo.
- **Rechazo**: con un itinerario en curso (modo distinto de `IDLE`) la orden se rechaza con aviso;
  destino inalcanzable en ambos sentidos, sin ruta A* o sin velocidad → aviso y sin tocar el tren.
  A mitad de misión, si el tren sale de la ruta y no hay forma de replanificar hacia el destino (o el
  destino desaparece), la misión **falla con aviso**; un **guardián de estancamiento** (`onTick`,
  ~1 hora de juego parado sin espera de bloque/horario/carga) la falla también. La consola recibe los
  avisos por `CommandManager.setWarningSink`; los scripts solo al log.
- **Determinismo**: la misión no usa reloj de pared ni azar; el comando se journaliza y se reproduce
  igual sobre una copia (test en `TrainMissionIntegrationTest`).

## Enlaces Relacionados
- [[../architecture/AutoPilotAnalysis|Análisis Detallado del AutoPilot]]
- [[../adr/ADR-008-Itinerary-Redesigned|ADR-008: Rediseño del Itinerario]]
- [[../adr/ADR-012-AutoPilot-Simplification|ADR-012: Simplificación del AutoPilot]]
- [[../adr/ADR-014-SafetyManager-Autopilot-Decoupling|ADR-014: Desacoplamiento de SafetyManager y AutoPilot]]

