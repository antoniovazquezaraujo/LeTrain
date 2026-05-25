# Plan de Implementación - Refactorización de Bloqueos Orientada a Eventos

Este plan describe la transición del sistema de seguridad de un modelo basado en sondeo (polling en cada tick) a uno completamente **orientado a eventos**, tal y como ha propuesto el usuario.

## User Review Required

> [!IMPORTANT]
> **Cambios Arquitectónicos Clave**:
> 1. **Liberación de Segmentos al salir del Fork (Evento)**: Eliminaremos la comprobación física en cada tick de la posición de los vagones. En su lugar, el sistema detectará el evento en el que el último vagón del tren abandona físicamente un desvío (`TrainMovementManager` y `notifyForkExit`). En ese preciso instante, se liberarán todos los segmentos anteriores que posea el tren.
> 2. **Suscripción a Liberación de Cantones (Centralizado)**: Añadiremos métodos en `BlockManager` para permitir que un tren se registre como "en espera" de un cantón específico. Cuando un cantón sea liberado, el gestor notificará exclusivamente a los trenes en espera para que intenten reclamarlo. Esto elimina por completo los temporizadores de reintentos en cada tick.
> 3. **Desacoplamiento del Frenado**: El `TrainSafetyManager` dejará de modificar directamente la velocidad de la locomotora. Su única responsabilidad será gestionar el flag lógico `permissionToMove`. Será la propia `Locomotive` la que, en su actualización física, aplique el frenado (`targetSpeed = 0`) si no dispone de permiso de movimiento.

## Proposed Changes

### Componente de Segmentos y Bloqueos

#### [MODIFY] [BlockManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/BlockManager.java)
- Declarar métodos para registrar y cancelar el registro de trenes en espera por un segmento:
  ```java
  void registerWaiting(Train train, Segment segment);
  void unregisterWaiting(Train train, Segment segment);
  ```

#### [MODIFY] [BlockManagerImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/impl/BlockManagerImpl.java)
- Implementar el mapa de trenes en espera: `Map<Segment, List<Train>> waitingTrains`.
- En `release()`, si un segmento queda libre de dueños, obtener la lista de trenes en espera y notificarles llamando a `train.onSegmentReleased(segment)`.

---

### Componente del Vehículo y Movimiento

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
- Implementar `onSegmentReleased(Segment segment)`: delegar en `safetyManager.onNextSegmentReleased()`.
- Implementar `notifyForkExit(ForkRailTrack fork)`: delegar en `safetyManager.releaseOldSegmentsOnForkExit()`.

#### [MODIFY] [TrainMovementManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainMovementManager.java)
- Al final del movimiento del último vagón (`linkerToMove == lastLinker`), si el cantón era un desvío, llamar a `train.notifyForkExit((ForkRailTrack) currentTrack)`.

#### [MODIFY] [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Locomotive.java)
- En `update()`, si es la locomotora directora, forzar el frenado si no hay permiso de avance:
  ```java
  if (getTrain() != null && !getTrain().getSafetyManager().hasPermissionToMove()) {
      setTargetSpeed(0);
  }
  ```

---

### Componente de Seguridad

#### [MODIFY] [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainSafetyManager.java)
- **Eliminar Sondeo y Modificaciones de Velocidad**:
  - Quitar el temporizador `safetyRetryTimer` y su lógica de decremento.
  - Quitar la llamada continua a `releaseOldSegments()` en `checkSafety()`.
  - Quitar la asignación directa de velocidad `targetSpeed = 0` en `checkSafety()`.
- **Implementar Lógica por Eventos**:
  - En `checkSafety()`, si no se consigue el bloqueo de `nextSegment`, registrarse en el gestor:
    ```java
    bm.registerWaiting(train, nextSegment);
    ```
  - Implementar `onNextSegmentReleased()`: reintentar el bloqueo del cantón deseado de forma inmediata. Si se consigue, marcar `permissionToMove = true` y des-registrar el tren del gestor.
  - Implementar `releaseOldSegmentsOnForkExit()`: liberar todos los segmentos propiedad del tren que no sean el actual ni el siguiente.

## Verification Plan

### Automated Tests
- Ejecutar `mvn clean test` para asegurar que el comportamiento básico e itinerarios sigan pasando.
- Escribir pruebas unitarias en `TrainSafetyManagerTest.java` para verificar la suscripción de eventos de liberación y el flujo de salida de Fork.
