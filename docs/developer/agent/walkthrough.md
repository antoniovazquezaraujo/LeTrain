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
- **Encapsulación de física de velocidad**: Se implementaron `initiateBraking()` and `restoreSpeed(int speed)` en `Train.java` para asumir el control directo de la locomotora (`directorLinker`) y desacoplar por completo a `TrainSafetyManager` de las alteraciones directas de velocidad de tracción.

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
  Si la ruta del piloto automático indica un próximo segmento diferente al segmento que el tren va a entrar físicamente (`findNextSegmentTopological`), se produce un **mismatch**. Si la aguja física (fork) está ocupada por el vehículo, el sistema de seguridad prioriza el segmento físico real. Al intentar bloquear el segmento físico real (`S1`), si este está ocupado, el tren frena y se detiene proactivamente en lugar de colisionar. Si la aguja está libre, el autopilot la alineará a tiempo y se continúa usando el segmento de la ruta.

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

---

## Soporte de Configuración Push (Trenes Empujados / Vagón en Cabeza)

### El Problema
Cuando el tren circula en marcha atrás (configuración push), la locomotora (el `directorLinker`) se sitúa en la parte posterior del tren empujando a los vagones. En esta disposición, el primer vehículo físico que pisa un sensor o aguja es un simple vagón (un `Linker` no-tractor, por ejemplo, de tipo `Wagon`), mientras que el `directorLinker` sigue en la parte trasera.
Anteriormente, al activar el piloto automático:
- Se invocaba `checkWaypointOnActivation()` en `Train.java` que buscaba waypoints a partir del `directorLinker` (la locomotora), ignorando que el frente real del tren ya estaba posicionado sobre el sensor del waypoint.
- En `TrainSafetyManager.java`, la búsqueda de cantones futuros (`findNextSegment`), el control de movimientos y las liberaciones se realizaban siempre asumiendo que la cabeza física del tren era `train.getDirectorLinker()`. Al ir marcha atrás, esto provocaba que se calculasen cantones partiendo de la locomotora (en la cola) en lugar de partir del vagón de cabeza, dando lugar a bloqueos tardíos de cantones e invasiones.

### La Solución
1. **Identificación Dinámica del Frente Físico (`getPhysicalFront()`)**:
   Hemos implementado `getPhysicalFront()` in `Train.java` para retornar dinámicamente el linker que se sitúa a la vanguardia de la marcha real:
   - Si la locomotora no está invertida (`!isReversed()`), el frente es `getFront()` (el primer elemento en la cola de linkers).
   - Si la locomotora está invertida (`isReversed()`), el frente es `getBack()` (el último elemento de la cola, que es el que lidera físicamente).
2. **Chequeo de Waypoints Reactivo al Activar Autopilot**:
   Hemos adaptado `checkWaypointOnActivation()` para que utilice `getPhysicalFront()`, permitiendo detectar correctamente la llegada al waypoint inicial de un tren que arranca empujado por una locomotora trasera.
3. **Gestión de Bloques y Cantones en el Frente de Marcha**:
  - Hemos adaptado todas las referencias de `(Linker) train.getDirectorLinker()` a `train.getPhysicalFront()` in `TrainSafetyManager.java` (`hasPermissionToMove`, `acquireInitialLocks`, `onSegmentEntered`, `onReverse`, y `releaseOldSegments`). De este modo, el sistema de seguridad y el cálculo de la topología física para bloquear cantones futuros se inician en el vehículo de cabeza real (sea locomotora o vagón).

---

## Liberación Dinámica de Cantones en Transición de Bifurcaciones (Forks)

### El Problema
Cuando el tren A está detenido en un apartadero (ramal A) esperando a que el tren B pase en sentido contrario por el ramal B, el tren A no reanudaba la marcha cuando el tren B *entraba* al ramal B, sino que tenía que esperar a que el tren B *saliese* de este por completo.

- **Causa**: Previamente, la liberación de cantones antiguos (`releaseOldSegments()`) solo se invocaba dentro de `onSegmentEntered()`, el cual se dispara **únicamente** cuando la cabeza del tren cruza una frontera de cantón.
- Como consecuencia, cuando el tren B avanzaba por el ramal B y su cola despejaba físicamente el cantón común de entrada, ningún evento `onSegmentEntered()` se disparaba (ya que el frente del tren B seguía dentro del ramal B). El cantón común de entrada quedaba bloqueado por el tren B de forma residual y artificial. Solo al llegar la cabeza del tren B al final del ramal B y pisar el siguiente cantón se liberaba el cantón común, permitiendo que el tren A reanudara su marcha con retraso.

### La Solución
Para resolver esto de forma puramente reactiva y **sin realizar comprobaciones costosas en cada ciclo de física (ticks)**:
- Hemos añadido ganchos de evento en [TrainMovementManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainMovementManager.java#L149-L165).
- Invocamos `safetyManager.releaseOldSegments(...)` de forma reactiva únicamente cuando:
  1. La cabeza del tren entra en un fork (`nextTrackOfLinker instanceof ForkRailTrack` con `firstLinker`).
  2. La cola del tren abandona por completo un fork (`currentTrack instanceof ForkRailTrack` con `lastLinker`).
- De esta manera, en el instante exacto en que la cola del tren de paso sale del desvío de la bifurcación (fork aguja), se desencadena la liberación de los segmentos que se han quedado atrás. Esto despierta instantáneamente al tren en espera del apartadero para que prosiga su itinerario, sin necesidad de realizar ejecuciones ni verificaciones en cada avance periódico (sin ticks).

---

## Búsqueda de Vía Alternativa (Siding / Ramal Paralelo)

### El Problema
Cuando dos trenes se encontraban ante bifurcaciones, se producía un bloqueo mutuo permanente (deadlock) porque ambos intentaban bloquear el mismo segmento planificado que ya estaba ocupado por el otro, a pesar de que existían apartaderos o ramales paralelos libres que habrían permitido a uno de ellos continuar.

### La Solución
Hemos implementado un mecanismo de **búsqueda dinámica de ramales alternativos**:
1. **Detección**: Cuando `tryLock(train, nextSegment)` falla, antes de iniciar el frenado, el `TrainSafetyManager` intenta buscar una alternativa viable llamando a `tryAlternativeSegment(model)`.
2. **Criterios de Elegibilidad**:
   - Solo se considera un segmento alternativo directo (`sAlt`) si conecta **exactamente** los mismos dos nodos extremos (`RailNode`/Forks) que el original (`nextSegment`).
   - No debe haber waypoints pendientes en el itinerario del tren sobre el segmento original.
   - El segmento alternativo debe estar libre y ser bloqueable (`bm.tryLock`).
3. **Redireccionamiento**: Si se reserva con éxito la vía alternativa, se actualiza el cantón en el gestor de seguridad, se modifica la ruta activa del piloto automático con `ap.replaceRouteSegment(oldNext, sAlt)` (lo que actualiza y orienta las agujas físicas de los desvíos automáticamente) y el tren prosigue sin detenerse.

---

## Corrección de Inicialización en Dos Pasos (Two-Pass Initialization) al Cargar Partida

### El Problema
Al cargar una partida guardada (`savegame.dat`), el cargador iteraba por cada tren e invocaba `rebind()`, el cual reclamaba los cantones físicamente ocupados (`claimOccupiedSegments`) y luego adquiría los bloqueos iniciales del piloto automático (`acquireInitialLocks`). 
Esto causaba un problema de ordenación secuencial: si el Tren 1 (ej. en `S3`) intentaba realizar su lookahead `acquireInitialLocks()` y bloquear el siguiente segmento (`S1`), lo lograba con éxito porque el Tren 2 (físicamente situado en `S1`) aún no había sido cargado en el bucle y, por lo tanto, no había reclamado la propiedad de `S1`. 
Cuando el cargador procesaba al Tren 2 más adelante, este fallaba al intentar reclamar su propia vía ocupada `S1` porque el Tren 1 la tenía reservada a modo preventivo. Esto desembocaba en falsas alarmas de colisión, detenciones no deseadas e incongruencias de estado tras restaurar el juego.

### La Solución
Hemos modificado el flujo de carga en [Model.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/Model.java#L204-L224) para realizar la inicialización de los trenes en **dos fases diferenciadas**:
1. **Paso de Presencia Física (Pass 1)**: Todos los trenes cargan sus datos de estado, reconectan sus escuchas y ejecutan `claimOccupiedSegments()`. De esta forma, cada tren asegura primero el cantón en el que se encuentra posicionado físicamente antes de que ningún otro tren realice reservas predictivas.
2. **Paso de Lookahead de Autopilot (Pass 2)**: Una vez asegurada la presencia física de todos los trenes en la red, se itera de nuevo y se llama a `acquireInitialLocks()` para calcular la ruta actual del piloto automático de cada tren y reservar preventivamente los cantones siguientes.

Esta separación elimina por completo los conflictos de prioridad en la carga de partidas, asegurando un inicio de simulación consistente y previniendo detenciones anómalas en bifurcaciones y agujas de entrada a apartaderos.

