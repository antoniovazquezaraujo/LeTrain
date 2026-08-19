[ [Índice] ] [[docs/architecture/Overview|⬅️ Arquitectura]] · [[docs/Index|⬅️ Volver al Índice]]

# Bucle de Movimiento de Vehículos

## Visión General

Cada tick del juego, las locomotoras avanzan por las vías en un pipeline de varios niveles: física, seguridad, control, eventos. El movimiento es **turn-based** (no continuo) y se ejecuta en un sistema de **dos pasadas** (validar todo, luego ejecutar).

```
SimulationController.tick()
  │
  └─ moveVehicles()
       └─ Por cada locomotora:
            └─ Locomotive.update()
                 ├─ ¿Puede moverse este tick? (turns == 0)
                 ├─ safetyManager.hasPermissionToMove()?
                 └─ movementManager.advance()
                      └─ moveLinkers() ← el núcleo
```

---

## El Pipeline Completo

```
Locomotive.update()  ← se llama cada tick del juego (~20 TPS)
  │
  ├─ GUARDAS: salida temprana si...
  │   ├─ isDestroying() → return (locomotora destruida)
  │   ├─ !isDirectorLinker() → return (solo la directora mueve el tren)
  │   ├─ !engineOn → return (motor apagado)
  │   └─ logisticsManager.isLoading() → return (cargando/descargando)
  │
  ├─ ACELERACIÓN DESDE 0:
  │   └─ Si currentSpeed==0 && targetSpeed>0 → updateInertia(), resetTurns()
  │
  ├─ ¿TIEMPO DE MOVERSE? (turns == 0)
  │   ├─ safetyManager.hasPermissionToMove()
  │   │   ├─ Modo manual → siempre true
  │   │   └─ Modo AUTO → isWaitingForBlock?
  │   │       └─ true → solo permite si speed>0 (inercia restante)
  │   │
  │   ├─ [PERMISO CONCEDIDO] → movementManager.advance()
  │   │   ├─ ¿Stalled? → no mover
  │   │   ├─ refreshLinkersDirection() → sentido de cada linker
  │   │   └─ moveLinkers() → ver detalle abajo
  │   │
  │   └─ [PERMISO DENEGADO] (solo AUTO):
  │       ├─ updateInertia() → frena gradualmente
  │       └─ resetTurns()
  │
  ├─ updateInertia() → ajusta currentSpeed hacia targetSpeed
  │
  ├─ resetTurns() → turns = 50 / currentSpeed
  │   └─ Sincroniza otras locomotoras del tren
  │
  └─ updateLimitedSpeed() → clamp por restricciones de vía
```

---

## moveLinkers() — El Corazón del Movimiento

`moveLinkers(boolean isNormalSense)` está en `TrainMovementManager` y es el método que **físicamente mueve los linkers (vagones) una celda**. Usa un sistema de **dos pasadas** con rollback:

```
advance()
  ├─ Guardas: isLoading? stalled? hasPermissionToMove?
  ├─ Guarda direcciones actuales (para rollback visual)
  ├─ refreshLinkersDirection() → calcula dirección de cada linker
  └─ moveLinkers(normalSense)
       ├─ FASE 1: Validar que TODOS pueden moverse
       ├─ FASE 2: Mover a TODOS físicamente
       └─ FASE 3: Post-move (colisiones, dead-end)
  Luego, si moveLinkers falló o el tren está stalled:
  └─ Restaurar direcciones guardadas (renderizado correcto)
```

---

### FASE 1: VALIDACIÓN (Pass 1)

Fase 1 construye 4 mapas: `currentTracks`, `entryDirsMap`, `occupyingLinkerMap`, `targetTracks`. Si algo falla, se llama a `clearReservations()` y se retorna `false` — ningún linker se mueve.

```
Orden de movimiento:
  isNormalSense=true  → linkers en orden natural (cabeza → cola)
  isNormalSense=false → linkers en orden inverso (cola → cabeza)
  (la locomotora directora define el sentido)

Por cada linkerToMove en movingOrder:

  1. Obtener currentTrack = linkerToMove.getTrack()
     └─ null → clearReservations(), return false

  2. Obtener exitDir = linkerToMove.getDir()
     (dirección hacia la que apunta el linker)

  3. nextTrack = currentTrack.getConnected(exitDir)
     └─ null → clearReservations(), return false
          (callejón sin salida — se detecta post-move en Fase 3)

  4. occupyingL = nextTrack.getLinker()
     └─ ¿Hay un ocupante de OTRO tren?
         ├─ speed >= CRASH_SPEED_THRESHOLD (5):
         │    crash(occupyingL, speed) → destruye ambos trenes
         │    clearReservations(), return false
         └─ speed < 5:
              notifyContact() → evento de contacto suave
              Parar tractores de ambos trenes (speed=0, targetSpeed=0)
              clearReservations(), return false

  5. entryDir = exitDir.inverse()
     (dirección de entrada: opuesta a la de salida)

  6. ¿nextTrack.canEnter(entryDir, linkerToMove)?
     └─ false → clearReservations(), return false
          (el linker no cabe en la celda destino — ej: linker largos)

  7. Guardar en mapas:
     currentTracks.put(linker, currentTrack)
     entryDirsMap.put(linker, entryDir)
     occupyingLinkerMap.put(linker, occupyingL)

  8. nextTrack.setReservation(linkerToMove)
     targetTracks.add(nextTrack)
     (reserva la celda para evitar conflictos entre linkers del mismo tren)

  → Si todo OK: continuar a Fase 2
```

**Detalle clave**: La reserva (`setReservation`) evita que dos linkers del mismo tren intenten ocupar la misma celda. Como los linkers se procesan en orden (cabeza→cola), el linker de cabeza reserva su destino, y el siguiente linker no puede meterse en esa misma celda (a menos que sea el que el de cabeza acaba de desocupar).

---

### FASE 2: EJECUCIÓN (Pass 2)

Fase 2 itera los linkers en **el mismo orden** que Fase 1, usando los mapas guardados. Cada linker:

```
Por cada linkerToMove (en movingOrder):
  ├─ Cargar estado guardado:
  │   currentTrack = currentTracks.get(linkerToMove)
  │   nextTrack = targetTracks.get(i)
  │   entryDir = entryDirsMap.get(linkerToMove)

  ├─ A: EVENTOS DE SALIDA (solo para el ÚLTIMO linker)
  │   Si linkerToMove == lastLinker:
  │   ├─ Sensor → sensorExit.onExitTrain(train)
  │   ├─ Semáforo → currentTrack.getSemaphore().onExitTrain(train)
  │   └─ Fork → ((ForkRailTrack)currentTrack).onExitTrain(train)
  │   (el último linker en abandonar una celda dispara
  │    los eventos de salida de esa celda)

  ├─ B: DESVINCULAR DE CELDA ACTUAL
  │   linkerToMove.setPreviousTrack(currentTrack)  ← para rollback visual
  │   linkerToMove.setPreviousDir(linkerToMove.getDir())
  │   currentTrack.removeLinker()                   ← libera la celda

  ├─ C: ENTRAR EN NUEVA CELDA
  │   nextTrack.enterLinkerFromDir(entryDir, linkerToMove)
  │   └─ FALLO → ROLLBACK INMEDIATO:
  │       linkerToMove.setTrack(currentTrack)
  │       currentTrack.setLinker(linkerToMove)
  │       linkerToMove.setPreviousTrack(null)
  │       linkerToMove.setPreviousDir(null)
  │       clearReservations(targetTracks)
  │       return false
  │   (enterLinkerFromDir falla solo si la celda destino
  │    está ocupada o reservada por otro vehículo —
  │    ver TrackDirector.canEnter())

  ├─ D: CONTADOR DE RAÍLES
  │   linkerToMove.railsSinceStop++
  │   (para el cálculo de inercia)
De Hola Y 
  ├─ E: ENTRADA A BIFURCACIÓN + CAMBIO DE SEGMENTO (solo primer linker)
  │   Si linkerToMove == firstLinker:
  │   ├─ Si nextTrack es ForkRailTrack:
  │   │   train.notifyForkEntry(fork)
  │   │   (no-op actualmente, pero aquí pertenece lógicamente)
  │   │
  │   └─ Cambio de segmento (si hay modelo):
  │       ├─ Obtener RailwayGraph
  │       ├─ Si nextTrack es ForkRailTrack Y tenemos nextSegment:
  │       │   newSegment = safetyManager.getNextSegment()
  │       │   (caso especial: el segmento destino ya se conoce)
  │       └─ Si no:
  │           newSegment = graph.getSegment((RailTrack)nextTrack)
  │           (buscar el segmento al que pertenece la vía)
  │       └─ Si newSegment != null Y es distinto del actual:
  │           train.notifySegmentEntered(newSegment)
  │           ├─ actionManager.checkWaypointArrival()
  │           ├─ autopilot.onSegmentEntered() → ruta + forks
  │           └─ safetyManager.onSegmentEntered() → bloqueos

  ├─ F: LIMPIAR RESERVA
  │   nextTrack.setReservation(null)
  │   (la celda ya está ocupada físicamente, no necesita reserva)

  └─ G: EVENTOS DE ENTRADA (solo para el PRIMER linker)
      Si linkerToMove == firstLinker:
      ├─ Sensor → sensorEnter.onEnterTrain(train)
      ├─ Semáforo → nextTrack.getSemaphore().onEnterTrain(train)
      └─ Fork → ((ForkRailTrack)nextTrack).onEnterTrain(train)
      (el primer linker que entra en una celda dispara
       los eventos de entrada de esa celda)
```

**Orden de eventos crítico**: Los eventos de SALIDA se disparan cuando el **último** linker abandona la celda, mientras que los de ENTRADA se disparan cuando el **primero** llega. Esto significa que un sensor en una celda de 1 rail dispara ambos eventos en el mismo tick (primero sale el último, luego entra el primero).

---

### FASE 3: POST-MOVIMIENTO

Después de mover todos los linkers con éxito, se verifica la **siguiente celda** del primer linker:

```
Obtener currentFirstTrack = firstLinker.getTrack()
Obtener nextAfterMove = currentFirstTrack.getConnected(firstLinker.getDir())

├─ ¿nextAfterMove EXISTE?
│   ├─ blockingLinker = nextAfterMove.getLinker()
│   │
│   ├─ ¿blockingLinker DE OTRO TREN?
│   │   ├─ speed >= 5 → crash(blockingLinker, speed)
│   │   │   ├─ notifyCrash() en ambos trenes
│   │   │   ├─ destroy() todos los linkers
│   │   │   ├─ stalled=true
│   │   │   └─ releaseAll() en blockManager
│   │   │
│   │   └─ speed < 5 → notifyContact()
│   │       ├─ notifyContact() en ambos trenes
│   │       └─ Parar tractores (speed=0, targetSpeed=0)
│   │
│   └─ Tras colisión: correctDirection(firstLinker)
│       (reorienta el linker para que apunte a una dirección
│        válida, ya que la colisión lo dejó apuntando a una
│        celda ocupada/destruida)
│
└─ ¿nextAfterMove == null? → CALLEJÓN SIN SALIDA
    ├─ speed >= 5 → CRASH
    │   ├─ Verificar que no se está ya destruyendo (flag alreadyDestroying)
    │   ├─ notifyCrash()
    │   ├─ Parar y destruir todos los linkers
    │   └─ stalled=true
    │
    └─ speed < 5 → CONTACTO
        ├─ notifyContact()
        └─ Parar tractores (speed=0, targetSpeed=0, forceIdleSound=true)
```

---

### ROLLBACK: Restauración de Direcciones

Si `moveLinkers()` falla o el tren queda `stalled`, `advance()` **restaura las direcciones** de todos los linkers (excepto el primero si hubo crash):

```
advance()
  ├─ Guarda savedDirs y savedEntryDirs ANTES de moveLinkers
  ├─ refreshLinkersDirection() → actualiza direcciones
  ├─ moveLinkers() → puede cambiar direcciones en colisiones
  │
  └─ Si !moved O stalled:
      Por cada linker (excepto first en crash):
      ├─ l.setDir(savedDir)
      └─ l.setEntryDir(savedEntry)
```

Esto es necesario porque `refreshLinkersDirection` modifica las direcciones de los linkers ANTES de moverlos. Si el movimiento falla, las direcciones apuntan a celdas inválidas y el renderizador dibujaría los vagones mirando hacia ningún lado.

---

### Ejemplo: Tren de 3 linkers avanzando 1 celda

```
Estado inicial:
  Celda:  [A]  [B]  [C]  [D]  [E]
  Tren:   L1→  L2→  L3→
          (L1=cabeza, L2=medio, L3=cola)

FASE 1 (validación):
  L1: currentTrack=[C], exitDir=ESTE, nextTrack=[D], sin ocupante → OK
  L2: currentTrack=[B], exitDir=ESTE, nextTrack=[C], sin ocupante (L1 ya reservó [D]) → OK
  L3: currentTrack=[A], exitDir=ESTE, nextTrack=[B], sin ocupante → OK

FASE 2 (ejecución):
  L3 (último): onExitTrain en [A] → sin sensor → nada
               sale de [A], entra en [B]
  L2:          sale de [B], entra en [C]
  L1 (primero): entra en [D] → onEnterTrain en [D] → eventos
                notifySegmentEntered si [D] es otro segmento

FASE 3 (post-move):
  nextAfterMove = [D].getConnected(ESTE) = [E]
  [E] sin ocupante → OK

Estado final:
  Celda:  [A]  [B]  [C]  [D]  [E]
  Tren:        L3→  L2→  L1→
```

---

## Sistema de Velocidad e Inercia

### Relación Velocidad-Turnos

```
Speed  Turns  Celdas/segundo (a 20 TPS)
─────  ─────  ─────────────────────────
  0     -1    0 (inactivo)
  1     50    0.4
  2     25    0.8
  3     17    1.2 (aprox)
  4     12    1.6 (aprox)
  5     10    2.0
  6      8    2.5 (aprox)
  7      7    2.9 (aprox)
  8      6    3.3 (aprox)
  9      5    4.0 (aprox)
 10      5    4.0
```

```
turns = 50 / currentSpeed  (redondeo hacia arriba implícito)
```

### Inercia (updateInertia)

La velocidad cambia según **raíles recorridos**, no según tiempo:

```
railsSinceLastSpeedChange++
factor = isBraking() ? 1 : 2   ← frena el doble de rápido
neededRails = max(1, currentSpeed * factor)

Si railsSinceLastSpeedChange >= neededRails:
  ├─ if currentSpeed < targetSpeed → currentSpeed++   (acelerar)
  └─ if currentSpeed > targetSpeed → currentSpeed--   (frenar)
  └─ railsSinceLastSpeedChange = 0
```

**Ejemplo: acelerar de 0 a 5**

```
Raíl  Acción
────  ──────
  0   targetSpeed=5, currentSpeed=0
      Acelerando: necesita 0*2=0 → currentSpeed=1
  2   currentSpeed(1)*2=2 → currentSpeed=2
  6   currentSpeed(2)*2=4 → currentSpeed=3
 12   currentSpeed(3)*2=6 → currentSpeed=4
 20   currentSpeed(4)*2=8 → currentSpeed=5
```

**Ejemplo: frenar de 5 a 0**

```
Raíl  Acción
────  ──────
  0   targetSpeed=0, currentSpeed=5
      Frenando: necesita 5*1=5 → currentSpeed=4
  9   currentSpeed(4)*1=4 → currentSpeed=3
 13   currentSpeed(3)*1=3 → currentSpeed=2
 16   currentSpeed(2)*1=2 → currentSpeed=1
 18   currentSpeed(1)*1=1 → currentSpeed=0
```

---

## Seguridad: Sistema de Bloques

### Estados de un Segmento

```
Segmento (tramo de vía entre dos nodos)
  │
  ├─ LIBRE: ningún tren lo ocupa ni lo ha reservado
  ├─ RESERVADO (tryLock): un tren lo ha bloqueado exclusivamente
  ├─ OCUPADO (shunting): varios trenes parados (speed=0) lo comparten
  └─ INVADIDO: un tren entró sin permiso → emergencyStop
```

### Ciclo de Bloqueo

```
1. Locomotive.setTargetSpeed(speed>0)
   └─ acquireInitialLocks()
       ├─ tryLock(currentSegment) → si falla → emergencyStop
       └─ tryLock(nextSegment)
           └─ si falla → initiateBraking(), isWaitingForBlock=true

2. Cada tick: safetyManager.hasPermissionToMove()
   └─ Modo AUTO y isWaitingForBlock:
       └─ ¿speed > 0? → permite (inercia restante)
       └─ ¿speed == 0? → bloqueado (espera)

3. notifySegmentEntered(newSegment)
   └─ safetyManager.onSegmentEntered()
       ├─ tryLock(currentSegment) → si falla → emergencyStop
       ├─ findNextSegment() + tryLock(nextSegment)
       │   └─ si falla → initiateBraiding()
       └─ releaseOldSegments()
           └─ libera segmentos que ya no ocupa físicamente

4. BlockManager.onReleaseListener (cuando se libera un segmento)
   └─ tryLock(nextSegment) → si éxito:
       ├─ isWaitingForBlock = false
       └─ restoreSpeed(savedTargetSpeed)
```

### Modos de Bloqueo

| Modo | Método | Uso |
|---|---|---|
| Exclusivo | `tryLock(train, segment)` | Un solo tren, en movimiento o parado |
| Compartido | `tryShuntingLock(train, segment)` | Varios trenes, todos speed=0 |

---

## Colisiones

### Umbral de Velocidad

| Speed | Evento | Efecto |
|---|---|---|
| < 5 | `notifyContact()` | Tren se para, sin daños. Dispara audio "contact" |
| >= 5 | `notifyCrash()` | Destrucción total de ambos trenes. Dispara audio "explosion" |

### Niveles de Detección

Las colisiones se detectan en **3 momentos distintos** durante el movimiento:

```
FASE 1 (validación): Ocupante en la celda destino
  ├─ Mismo tren → ignorar (movimiento normal)
  └─ Otro tren → crash/contact según velocidad

FASE 3 (post-movimiento): Ocupante en la celda siguiente
  ├─ Se acaba de mover, verificar colisión inmediata
  └─ crash/contact según velocidad

FASE 3 (post-movimiento): Callejón sin salida
  ├─ No hay celda siguiente
  └─ crash/contact según velocidad
```

---

## Autopilot y Ruteo

El autopilot **no controla la velocidad directamente**. Solo planifica rutas y orienta desvíos. La velocidad la controla el sistema de bloques:

```
Activación del piloto automático
  └─ setAutoMode(true) + activate()
      ├─ onSegmentEntered(currentSegment)
      │   ├─ calculateRoute()
      │   │   └─ pathfinder.find(currentSeg, targetSeg)
      │   │       └─ BFS en RailwayGraph
      │   └─ ensureForkRoute() → orienta desvíos
      └─ acquireInitialLocks() → inicia bloqueo de segmentos

Control de velocidad (indirecto):
  ├─ Bloque siguiente LIBRE → velocidad normal (la que tenga targetSpeed)
  ├─ Bloque siguiente OCUPADO → initiateBraking():
  │   ├─ Guarda savedTargetSpeed
  │   ├─ isWaitingForBlock = true
  │   └─ targetSpeed = 0
  │
  └─ Bloque se LIBERA → onBlockReleased():
      ├─ isWaitingForBlock = false
      └─ restoreSpeed(savedTargetSpeed)
```

---

## Waypoints

Los waypoints son puntos de ruta (estación o sensor) que ejecutan comandos al llegar:

```
Puntos de entrada para checkWaypointArrival():
  ├─ notifySegmentEntered() → cambio de segmento
  ├─ notifyEnterSensor() → entrada a celda con sensor
  └─ setTargetSpeed(>0 desde 0) → arranque

Comandos disponibles:
  ├─ WAIT(ticks)     → espera usando SimulationScheduler
  ├─ LOAD/UNLOAD     → 80 ticks por vagón
  ├─ REVERSE         → pendingReverse, se ejecuta al llegar a speed=0
  └─ SPEED(n)        → setTargetSpeed + acquireInitialLocks
```

---

## Diagrama de Clases

```
 ┌──────────────────┐
 │   Locomotive     │  (Tractor, Linker, Tracker)
 │  ───────────     │
 │  currentSpeed    │
 │  targetSpeed     │
 │  turns           │
 │  engineOn        │
 │  pendingRev.     │
 │  ───────────     │
 │  update() ◄─────────── SimulationService (cada tick)
 │  updateInertia() |
 │  resetTurns()    |
 └──────┬───────────┘
        │ dirige (directorLinker)
        ▼
 ┌──────────────────────────────────────────────────────┐
 │                      Train                           │
 │  ─────────────────────────────────────────────────── │
 │  movementManager  : TrainMovementManager             │
 │  safetyManager    : TrainSafetyManager               │
 │  actionManager    : TrainActionManager               │
 │  autopilot        : AutoPilot                        │
 │  couplingManager  : TrainCouplingManager             │
 │  logisticsManager : TrainLogisticsManager            │
 │  eventDispatcher  : TrainEventDispatcher             │
 │  ─────────────────────────────────────────────────── │
 │  notifyCrash() / notifyContact() / notifyLink()      │
 │  notifySegmentEntered() / notifyEnterSensor()        │
 └──────┬───────┬───────┬───────┬───────┬───────┬───────┘
        │       │       │       │       │       │
        ▼       ▼       ▼       ▼       ▼       ▼
    Movement  Safety  Action  Auto-  Coupling  Logistics
    Manager   Manager Manager  Pilot  Manager   Manager
        │       │
        │       └── BlockManager → RailwayGraph
        │
        └── moveLinkers() → Linker avanzan por RailTrack[]
```

---

## Archivos Clave

| Componente | Ruta |
|---|---|
| Locomotive | `src/main/java/letrain/vehicle/rail/impl/Locomotive.java` |
| Train | `src/main/java/letrain/vehicle/rail/impl/Train.java` |
| TrainMovementManager (impl) | `src/main/java/letrain/vehicle/rail/impl/TrainMovementManager.java` |
| TrainSafetyManager (impl) | `src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java` |
| TrainActionManager (impl) | `src/main/java/letrain/itinerary/impl/TrainActionManager.java` |
| AutoPilotImpl | `src/main/java/letrain/itinerary/impl/AutoPilotImpl.java` |
| TrainCouplingManager (impl) | `src/main/java/letrain/vehicle/rail/impl/TrainCouplingManager.java` |
| TrainLogisticsManager (impl) | `src/main/java/letrain/vehicle/rail/impl/TrainLogisticsManager.java` |
| BlockManagerImpl | `src/main/java/letrain/segments/impl/BlockManagerImpl.java` |
| RailwayGraphImpl | `src/main/java/letrain/segments/impl/RailwayGraphImpl.java` |
| TopologyServiceImpl | `src/main/java/letrain/segments/impl/TopologyServiceImpl.java` |
| RailIterator | `src/main/java/letrain/vehicle/rail/RailIterator.java` |
| Linker | `src/main/java/letrain/vehicle/rail/Linker.java` |
| Wagon | `src/main/java/letrain/vehicle/rail/impl/Wagon.java` |
