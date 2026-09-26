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
`stop at end`, `stop when blocked` y `stop on contact` (#645). La velocidad viaja en la orden y el tren
acaba parado; no son un plan que se repita, sino un trabajo puntual.

- **`TrainMission`** (`letrain.itinerary.TrainMission`): valor con tipo, destino, velocidad y estado
  (`ACTIVE`/`COMPLETED`/`FAILED`/`CANCELLED`). Se ejecuta dentro de `AutoPilotImpl` y es transitorio:
  no se serializa (al cargar una partida sin misión ni itinerario el autopilot vuelve a manual).
- **Aproximación de enganche (`stop on contact`, #645)**: sin ruta, sin curva de frenado y sin
  auto-inversión; el tren conduce a la velocidad de la orden hasta el primer contacto físico y
  completa parado y pegado al vehículo de delante (listo para `couple`). El contacto lo entrega
  `Train.notifyContact` al autopilot **antes** del dispatcher de eventos, para que las acciones del
  waypoint pendientes de la misión (p. ej. `couple`) se reanuden con la misión ya terminada. A
  velocidad ≥ umbral de choque no hay contacto: `crashDestroy` falla la misión con aviso (física
  normal). Si el tren **ya está pegado** (a un vehículo o al tope), la orden completa en el sitio:
  el chequeo instantáneo de arranque reporta la **velocidad real** (0), no la pedida (review M1), y
  arrancar pegado al tope completa sin esperar evento (review M2). Éxito silencioso (solo log),
  como el resto de misiones.
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
  ~1 hora de juego parado sin espera de bloque/horario/carga) la falla también. Los avisos de
  **problema** (rechazo, inalcanzable, ruta perdida, estancamiento) van por
  `CommandManager.setWarningSink` a la consola en órdenes tecleadas y al log en scripts; el **éxito**
  (llegada, fin de vía, bloqueo alcanzado, cancelación) solo queda en el log.
- **Determinismo**: la misión no usa reloj de pared ni azar; el comando se journaliza y se reproduce
  igual sobre una copia (test en `TrainMissionIntegrationTest`).

### Maniobras en waypoints (issue #626, ADR-022 phase 2f)
El plan del waypoint acepta las mismas órdenes de tren que los scripts y las ejecuta **en orden** al
llegar: `uncouple`/`couple`, misiones (`stop at …`, `stop at end`, `stop when blocked …`) y acciones
de fork (`fork N set straight|curved`, `fork N flip`). Detalles de implementación:

- Las misiones de waypoint son `TrainMission` con `Origin.ITINERARY`: **no auto-invierten** (aviso
  *"no route … from the current sense; add 'reverse'"*, solo cuando el destino está físicamente
  detrás) y al terminar devuelven el autopilot a `FOLLOWING` (no a `IDLE`), porque la maniobra es un
  paso del plan. El flujo espera a que el tren esté **totalmente parado** antes de la siguiente
  acción; si la misión completa **sincrónicamente** (ya en el destino, ya bloqueado, ya en el final)
  la acción siguiente arranca en el mismo paso (no se espera una parada que no va a llegar). Una
  maniobra **rechazada** aborta las acciones restantes de ese waypoint.
- Para misiones de waypoint la ruta se construye con el **paseo físico** (`walkRouteToTrack`), no con
  A*: así un `fork set curved` del autor no lo pisa `ensureForkRoute` al recalcular. Los tramos de
  las agujas se saltan al listar segmentos (son nodos compartidos). Si el paseo no encuentra el
  destino se cae al A* + `ensureForkRoute` como en los itinerarios.
- **Cantones**: al dividir un tren (`divideTrain`) las dos partes registran su presencia con
  `claimSharedPresence`/`rebindShared` (`BlockManager.addOwner`), **sin** parada de emergencia: el
  cantón queda ocupado por ambas hasta que la última lo abandone. Una misión de waypoint cuyo
  destino está en el cantón bloqueado **y cuyos ocupantes ajenos no tienen locomotora** (p. ej. los
  vagones desenganchados) lo **entra** como una maniobra manual y comparte la propiedad
  (`isShuntingMissionTarget`); con un tren ajeno (con locomotora) no hay exención y la misión espera
  y reanuda. Las comprobaciones físicas de movimiento siguen parando el tren antes de cualquier
  vehículo. Al cargar, los trenes solo-vagones también reclaman su cantón. Para `stop on contact`
  (#645) el destino se resuelve **dinámicamente al vehículo de delante** (`missionTargetSegment` →
  paseo físico), de modo que la exención funciona sin estación/sensor; y vale igual para la orden
  suelta que para la acción de waypoint (mismo criterio de seguridad: ningún ocupante ajeno con
  locomotora).
- **Bypass y waypoint alcanzado (#645 follow-up)**: `segmentHasPendingWaypoints` solo cuenta el
  waypoint actual mientras **no se ha alcanzado** (`AutoPilot.currentWaypointReached`, que marca
  `TrainActionManager.startWaypoint` al arrancar las acciones y se limpia al avanzar de waypoint).
  Una vez servido su stop, su cantón ya no bloquea el bypass por la variante paralela
  (`tryAlternativeSegment`): sin el flag, un run-around que deja sus vagones en el cantón del
  waypoint 0 se quedaba esperando para siempre al volver a cruzar ese cantón hacia otro destino.
- **Contacto y velocidad restaurada (#645 follow-up)**: las rutas de contacto no escriben velocidad
  **después** de `notifyContact`, porque la cadena del evento (misión → acciones del waypoint →
  `couple` → departure programado) puede restaurar legítimamente una velocidad; y `contactDetected`
  captura el tren ocupante **antes** de notificar y no frena al propio tren si el `couple` de la
  cadena ya lo ha absorbido. El chequeo instantáneo de arranque (`Locomotive.update`) para al tren
  antes de notificar, por el mismo motivo.
- Tras la maniobra, `advanceWaypoint` + `clearRoute` recalculan la ruta al siguiente waypoint desde
  la posición y el sentido en que haya quedado el tren.
- **Itinerarios desde consola**: cada sentencia tecleada se ejecuta con un `CommandManager` nuevo,
  así que `create itinerary` y `assign itinerary` deben ir en el mismo script/programa (el editor de
  programa y los escenarios usan un único `CommandManager`). Es un límite pre-existente, candidato a
  issue aparte.
- **Un itinerario necesita ≥2 waypoints** (es un bucle): un plan de un solo waypoint se rechaza con
  aviso y no se asigna; `set autopilot true` avisa si el tren no tiene itinerario válido.

## Enlaces Relacionados
- [[../architecture/AutoPilotAnalysis|Análisis Detallado del AutoPilot]]
- [[../adr/ADR-008-Itinerary-Redesigned|ADR-008: Rediseño del Itinerario]]
- [[../adr/ADR-012-AutoPilot-Simplification|ADR-012: Simplificación del AutoPilot]]
- [[../adr/ADR-014-SafetyManager-Autopilot-Decoupling|ADR-014: Desacoplamiento de SafetyManager y AutoPilot]]

