# Plan de Implementación — Inversión Automática y Sincronización en Desvíos (Forks)

Este plan describe la solución para evitar el bloqueo o deadlock de los trenes en piloto automático al llegar a desvíos o forks. El problema se debe a que el trazado físico a menudo requiere que el tren invierta su sentido de marcha para seguir su ruta (por ejemplo, de un ramal a otro pasando por el tronco central), pero el itinerario no contiene la orden `REVERSE` explícitamente, o el `TrainSafetyManager` no se entera de la inversión física del tren y mantiene información de bloqueos desfasada.

## User Review Required

> [!IMPORTANT]
> - **Inversión Automática Reactiva**: Los trenes en piloto automático ahora invertirán su marcha automáticamente si se detecta un desajuste (mismatch) entre la dirección física de avance y el siguiente segmento planificado por la ruta. Esto evita que el tren continúe en sentido contrario (lo que causaría colisiones o detenciones por failsafe).
> - **Sincronización del Safety Manager**: Cada vez que el tren invierta su sentido física o lógicamente (por comando HUD, script o automatización), el `TrainSafetyManager` es notificado para recalcular inmediatamente los bloques de vía libre en la nueva dirección y liberar los anteriores.
> - **Persistencia de Velocidad tras Reversa**: Al frenar para realizar una inversión, el tren guarda su velocidad anterior y la restaura automáticamente al cambiar de sentido, de forma que el piloto automático no se detenga de forma permanente.

## Open Questions
Ninguna en este momento. Las reglas físicas e interfaces del motor permiten una integración directa y limpia.

## Proposed Changes

### Componente: Control de Tren y Locomotora (`letrain.vehicle.rail.impl`)

---

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java)
- Declarar el campo `private transient int savedSpeedBeforeReverse = -1;` para almacenar la velocidad antes de frenar para invertir la marcha.
- Añadir el getter `public boolean isPendingReverse() { return pendingReverse; }`.
- En `executeCommand(WaypointCommand command)` para el caso `REVERSE`: guardar la velocidad objetivo actual (`savedSpeedBeforeReverse = dirLinker.getTargetSpeed();`) antes de establecerla a 0 e iniciar el frenado.
- En `notifySpeedChanged(int speed)`: cuando la velocidad llega a 0 y `pendingReverse` es verdadero, llamar a `dirLinker.toggleReversed()` y restaurar la velocidad guardada en `savedSpeedBeforeReverse` si esta es válida.

#### [MODIFY] [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Locomotive.java)
- En `toggleReversed()`: notificar al gestor de seguridad (`TrainSafetyManager`) de la inversión física mediante la llamada a `getTrain().getSafetyManager().onReverse((Model) getTrain().getModel())`.

#### [MODIFY] [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java)
- En `findNextSegment(Linker head, RailwayGraph graph)`:
  - Detectar si la ruta planificada (`routeNext`) discrepa del segmento físico real (`topological` es diferente de `routeNext`, o `topological` es `null` mientras `routeNext` es válido).
  - Si hay discrepancia y el tren está en modo automático y no está ya en proceso de inversión (`!train.isPendingReverse()`), loguear el evento y disparar la inversión automática llamando a `train.executeCommand(WaypointCommand.REVERSE)`.
  - Si la inversión se ejecuta inmediatamente (porque la velocidad era 0), volver a evaluar y retornar recursivamente `findNextSegment(head, graph)` bajo la nueva orientación física para evitar devolver un paso obsoleto (`S0` en lugar de `S4`).

---

### Componente: Tests de Integración (`letrain.itinerary`)

#### [MODIFY] [AutoPilotIntegrationTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotIntegrationTest.java)
- Añadir un nuevo test de integración `autoReverseOnRoutingMismatch()` que configure un trazado en Y (con un desvío que bifurca en dos ramales, A y B), ponga al tren en el ramal A mirando hacia el desvío, defina un itinerario hacia el ramal B sin comando `REVERSE`, y verifique que el tren avanza hasta el desvío/tronco, se para, invierte la marcha automáticamente y avanza con éxito hasta el ramal B.

## Verification Plan

### Automated Tests
- Ejecutar la suite completa de pruebas:
  ```bash
  mvn clean test
  ```
- Ejecutar específicamente la clase de integración modificada:
  ```bash
  mvn clean test -Dtest=AutoPilotIntegrationTest
  ```

### Manual Verification
- Iniciar el juego usando el savegame provisto por el usuario:
  ```bash
  java -jar target/JLeTrain-1.0-SNAPSHOT-jar-with-dependencies.jar
  ```
- Comprobar visualmente que los trenes ya no se detienen de forma permanente en las agujas de entrada y salida de las estaciones (desvíos/forks) y completan sus itinerarios cíclicos con éxito.
