# Walkthrough - Bloqueo de Segmentos y Orientación de Forks (Event-Driven)

Hemos refactorizado por completo el sistema de seguridad y bloqueos de cantones en LeTrain para pasar de un modelo de sondeo (polling) en cada tick a un modelo completamente **orientado a eventos**, solucionando los problemas de reintentos continuos, frenados inestables y asegurando el desacoplamiento de responsabilidades.

## Cambios Realizados

### 1. Registro y Suscripción de Eventos en Cantones
- **[BlockManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/BlockManager.java)** y **[BlockManagerImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/impl/BlockManagerImpl.java)**:
  - Añadidos métodos `registerWaiting(Train, Segment)` y `unregisterWaiting(Train, Segment)` para que los trenes se registren para esperar la liberación de un cantón bloqueado.
  - En `release()`, al liberarse el último dueño de un segmento, se notifica de inmediato a todos los trenes en la lista de espera mediante `train.onSegmentReleased(segment)` y se limpia la entrada.

### 2. Canales de Eventos en el Tren
- **[Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)**:
  - Implementado `onSegmentReleased(Segment)` que delega en el `safetyManager`.
  - Implementado `notifyForkExit(ForkRailTrack)` que delega en el `safetyManager`.

### 3. Detección Física de Salida de Desvío (Fork)
- **[TrainMovementManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainMovementManager.java)**:
  - Cuando el último vagón (`linkerToMove == lastLinker`) abandona físicamente un desvío (`ForkRailTrack`), se dispara `train.notifyForkExit()`.
  - Esto elimina la necesidad de comprobar en cada tick la posición de los vagones, liberando los cantones de forma proactiva y segura.

### 4. Desacoplamiento de Seguridad y Control de Velocidad
- **[Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Locomotive.java)**:
  - En la actualización física (`update()`), se eliminó la imposición continua en cada tick de `setTargetSpeed(0)` si `hasPermissionToMove()` es `false`. Esto permite que el usuario controle manualmente la velocidad sin que el sistema la fuerce constantemente a cero en cada tick, facilitando la separación manual de trenes.

### 5. Rediseño del Gestor de Seguridad
- **[TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainSafetyManager.java)**:
  - **Eliminación de Polling**: Eliminado el temporizador `safetyRetryTimer` y su decremento en cada tick.
  - **Gestión de Espera Activa**: Al evaluar la seguridad en transiciones de segmento (`checkSafety`), si no se puede bloquear el siguiente cantón (`nextSegment`), el tren se registra en el gestor de bloqueos como "en espera" y se mantiene en reposo.
  - **Reacción a Liberación**: Implementado `onNextSegmentReleased(Model, Segment)`, que se despierta cuando el gestor notifica la liberación del cantón. Si se logra bloquear en este momento, se otorga permiso de avance (`permissionToMove = true`).
  - **Liberación en Fork Exit**: Implementado `releaseOldSegmentsOnForkExit(Model)`, que se activa por evento y libera todos los cantones físicamente vacíos de vagones, conservando `currentSegment` y `nextSegment` para evitar fugas de memoria o bloqueos huérfanos.

### 6. Control de Velocidad y Transición a Modo Manual
- **[Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)**:
  - Añadido el método `deactivateAutoModeAndStop()` que desactiva el piloto automático (`autoMode = false`), detiene su ejecución y pone la velocidad a 0.
  - Modificado el método de avance físico (`advance()`) para que el bloqueo de seguridad (`!hasPermissionToMove()`) solo fuerce la detención a velocidad `0` y aborte la actualización si el tren se encuentra en modo automático (`isAutoMode()`). En modo manual, el usuario tiene pleno control físico sobre la tracción, permitiéndole maniobrar el tren de vuelta fuera de un desvío o cantón bloqueado.
- **[TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainSafetyManager.java)**:
  - Llama a `train.deactivateAutoModeAndStop()` en el preciso instante en que el tren es rechazado (sin permiso) o cuando ocurre un rebasamiento (overshoot), deteniendo el tren una única vez y pasándolo a control manual.

### 7. Restauración de la Alineación de Agujas (Fork Route)
- **[Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)**:
  - Restaurado el método original de alineación `ensureForkRoute(Segment, Segment)` y su helper `isAlternativeRouteNeeded` que determina la orientación correcta del desvío basándose directamente en el grafo (`node.getOutSteps()`). Esto soluciona la desalineación de desvíos introducida en versiones previas, la cual causaba que los trenes rebotaran o volvieran hacia atrás ("rollback") al entrar en las agujas.

### 8. Robustez del Contexto de Piloto Automático sobre Agujas
- **[TrainAutoPilotContext.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainAutoPilotContext.java)**:
  - Modificado `currentSegment()` para evitar que devuelva `null` cuando la locomotora de cabeza se encuentra encima de un desvío (Fork). Ahora busca progresivamente el primer vagón del tren que se encuentre sobre una vía de cantón estándar, y como alternativa final recupera el último cantón registrado en el gestor de seguridad (`safetyManager.getCurrentSegment()`). Esto garantiza que el piloto automático pueda re-calcular y seguir rutas desde un desvío en lugar de detenerse de forma indefinida por segmento nulo.

## Pruebas y Validación

### Pruebas Unitarias Implementadas
- **[TrainSafetyManagerTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/vehicle/impl/rail/TrainSafetyManagerTest.java)**:
  - Rediseñado completamente para reflejar el comportamiento por eventos.
  - `testCheckSafetyDeniedWhenLockFailsAndRegistersWaiting`: Valida que al fallar el bloqueo, el tren se registre correctamente en la lista de espera de `BlockManager`.
  - `testOnNextSegmentReleasedGrantsPermission`: Valida que al liberarse el cantón, el evento despierta al tren, intente el bloqueo y le otorgue permiso de avance.
  - `testReleaseOldSegmentsOnForkExit`: Valida que al salir de un Fork, se liberen los cantones antiguos y no los activos o físicamente ocupados.

### Resultados de la Suite de Pruebas
Hemos ejecutado la suite completa de pruebas:
- Comando: `mvn clean test`
- Resultado: **BUILD SUCCESS**
- Total de Pruebas Ejecutadas: **341** exitosas (0 fallos, 0 errores, 0 omitidos).
