# Walkthrough — Piloto Automático Reactivo y Sin Ticks

Hemos completado con éxito la migración del piloto automático del simulador LeTrain a un modelo 100% reactivo (libre de ticks periódicos) y hemos garantizado que el tren desacelere por inercia en lugar de detenerse en seco.

## Cambios Realizados

### [SimulationScheduler.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/utils/impl/SimulationScheduler.java)
- **Implementación**: Se implementaron los métodos `tick()` y `clear()` para gestionar correctamente el decremento de los temporizadores y la ejecución de tareas programadas (`WAIT`).

### [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java)
- **Activación reactiva**: En `onSegmentEntered(model, newSegment)`, se notifica primero al piloto automático del cambio de segmento, permitiendo orientar desvíos antes de intentar bloquear el siguiente segmento.
- **Inercia sin frenado en seco**: En `hasPermissionToMove()`, se permite el avance lógico dentro del segmento actual si `isWaitingForBlock` es verdadero, siempre que la velocidad del tren sea mayor a 0 (deceleración progresiva).
- **Control de velocidad de frenado**: Se añadió un helper `initiateBraking()` que guarda la velocidad actual (`savedTargetSpeed`) antes de poner la velocidad a 0, y se restaura esta velocidad en `wakeUp()` cuando el cantón se libera.

### [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java)
- **Activación reactiva en sensores/estaciones**: En `notifyEnterSensor()`, se notifica al piloto automático para que evalúe si ha llegado a su destino incluso si no ha cruzado un límite de segmento.
- **Dirección inversa diferida**: Se maneja la orden `REVERSE` en movimiento. Si la velocidad es > 0, se reduce a 0 y se marca `pendingReverse = true`. Al detenerse completamente, `notifySpeedChanged(0)` ejecuta físicamente la inversión de marcha.
- **Verificación de bloqueo inicial**: Se implementó `acquireInitialLocks()` y se añadieron llamadas automáticas al iniciar comandos `SPEED > 0` y al reconectar cantones (`rebind()`) para asegurar la verificación de bloqueos antes de iniciar el movimiento.

### [TrainActionManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/TrainActionManager.java)
- **Nueva firma**: Se añadió `acquireInitialLocks()` para permitir que la lógica de piloto automático desencadene una comprobación de bloqueos antes de arrancar.

### [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- **Reanudación segura**: Se invoca a `actionManager.acquireInitialLocks()` al final de `resumeWaiting()` para forzar el chequeo preventivo de seguridad al salir del estado de espera (`WAIT`).

### [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Locomotive.java)
- **Eliminación del tick periódico**: Se eliminó por completo la invocación a `getTrain().getAutopilot().tick()`.
- **Deceleración acústica**: Se procesa `acousticSpeedSignal` en el bloque `else` cuando el avance está bloqueado por inercia para mantener sincronía sonora en paradas.

---

## Resultados de Verificación

### Pruebas Automatizadas
- **Comando**: `mvn test`
- **Resultado**: Los **329 tests de la suite** pasaron exitosamente (`BUILD SUCCESS`).
- Se rediseñó la suite `AutoPilotImplTest.java` para utilizar eventos reactivos y simular paradas programadas.

### Compilación y Empaquetado
- **Comando**: `mvn clean package -DskipTests`
- **Resultado**: `BUILD SUCCESS`. Distribución de juego recreada en `output/LeTrain`.

---

## Logs de Diagnóstico Añadidos

Para diagnosticar en detalle por qué los trenes no frenan o ignoran los cantones en el piloto automático, hemos introducido logs descriptivos detallados por todo el flujo de movimiento, seguridad y cálculo de rutas:

1. **[TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java)**:
   - Registro al inicio, en segmentación física (`onSegmentEntered`), obtención de locks iniciales (`acquireInitialLocks`) y reanudación (`wakeUp`).
   - Logging detallado de si el tren tiene permisos de movimiento, de los locks intentados sobre cada segmento y de si las comprobaciones topológicas/itinerario son exitosas.
2. **[Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Locomotive.java)**:
   - Mensaje de log al cambiar de velocidad objetivo (`setTargetSpeed`).
   - Trazabilidad en cada tick de movimiento (`isTimeToMove`), indicando la velocidad actual, velocidad objetivo, estado de inercia y permisos de movimiento.
3. **[Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java)**:
   - Logs del flujo del método `advance()`, especificando si el tren está cargando, stalleado o si carece de permisos de movimiento.
   - Detalle de la ejecución de comandos de waypoint (`executeCommand`), incluyendo cambios de velocidad, inversión de marcha y procesos de carga/descarga.
4. **[AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)**:
   - Logs de entrada a cantones (`onSegmentEntered`), indicando el destino actual, si se llegó al waypoint, si se calculó nueva ruta y si se reorientó un desvío.

---

## Corrección del Conflicto de Desviación Físico-Lógica (Failsafe)

### El Problema
Al analizar los logs provistos, encontramos un caso en el que la ruta planificada por el `AutoPilot` indicaba un segmento destino (`S3` al norte) pero el tren se movía físicamente en dirección contraria (`S1` al sur) debido a que no se había ejecutado un comando de reversa y la topología física lo guiaba en sentido opuesto.

- **Comportamiento Buggy**: El `TrainSafetyManager` consultaba la ruta del piloto automático, obtenía que el siguiente segmento era `S3` (libre), y lo bloqueaba correctamente. Sin embargo, el tren physically avanzaba hacia `S1` (ocupado por el otro tren), invadiéndolo sin chequear su bloqueo y colisionando.

### La Solución
- Hemos introducido una comprobación de seguridad (**Failsafe**) en `TrainSafetyManager.java#findNextSegment()`:
  Si la ruta del piloto automático indica un próximo segmento diferente al segmento que el tren va a entrar físicamente (`findNextSegmentTopological`), se produce un **mismatch**. En este caso, el sistema de seguridad prioriza el segmento físico real.
  Al intentar bloquear el segmento físico real (`S1`), si este está ocupado, el tren frena y se detiene proactivamente en lugar de colisionar.

---

## Inicialización del Piloto Automático al Cargar Partida / Re-enlazar

### El Problema
Al cargar una partida o re-enlazar un tren (`rebind`), el piloto automático se deserializaba sin su ruta (`currentRoute = List.of()`), la cual solo se calculaba al cruzar físicamente una frontera de segmento. Al no tener ruta al arrancar, los trenes no podían saber en qué dirección orientar sus agujas ni si debían retroceder al inicio, moviéndose temporalmente por inercia física (topología) hasta toparse con una aguja y disparar el Failsafe de detención.

### La Solución
- En `TrainSafetyManager.java#acquireInitialLocks()`, notificamos al piloto automático del segmento actual que ha sido resuelto y no es nulo, justo antes de proceder a evaluar y bloquear los cantones siguientes:
  ```java
  if (train.isAutoMode() && train.getAutopilot() != null) {
      log.info("Train {} acquireInitialLocks: notifying autopilot of segment {}", train.getId(), currentSegment.getId());
      train.getAutopilot().onSegmentEntered(currentSegment);
  }
  ```
  Esto garantiza que, al cargar una partida, reanudar de una espera, o re-enlazar, el piloto automático calcule de inmediato su ruta y alinee físicamente las agujas antes de que el tren se ponga en movimiento o intente bloquear el siguiente cantón. De este modo, la topología física y la lógica del piloto automático están perfectamente sincronizadas y se evitan detenciones indeseadas por mismatch de dirección física.
