# Tareas de Implementación - Refactorización de Bloqueos Orientada a Eventos

- `[x]` Paso 1: Modificar `BlockManager.java` y `BlockManagerImpl.java` para implementar registro y notificación de trenes en espera (`registerWaiting`, `unregisterWaiting` y notificación en `release`)
- `[x]` Paso 2: Modificar `Train.java` para implementar `onSegmentReleased` y `notifyForkExit`
- `[x]` Paso 3: Modificar `TrainMovementManager.java` para notificar `notifyForkExit` al salir del Fork
- `[x]` Paso 4: Modificar `Locomotive.java` para aplicar el frenado proactivo (`targetSpeed = 0`) si no hay permiso de avance
- `[x]` Paso 5: Modificar `TrainSafetyManager.java` para eliminar sondeos y velocidad en `checkSafety`, y usar eventos de liberación y desvíos
- `[x]` Paso 6: Compilar y validar el proyecto con la suite de pruebas
- `[x]` Paso 7: Actualizar y escribir nuevas pruebas unitarias en `TrainSafetyManagerTest.java` para validar el modelo de eventos
