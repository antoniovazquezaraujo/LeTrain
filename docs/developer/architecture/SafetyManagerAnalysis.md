# Análisis de TrainSafetyManager

## Estado
Rediseñado en [[adr/ADR-015-Abstraccion-Puertos-Nodos]] para usar un enfoque puramente reactivo y basado en eventos (sin ticks).

---

## Variables de Estado

```
currentSegment      → el segmento donde está la cabeza
nextSegment         → el segmento que queremos ocupar después (pre-bloqueado)
isWaitingForBlock   → true cuando frenamos porque nextSegment está ocupado
insideFindNextSegment → flag de reentrancia para findNextSegment
```

---

## Flujo Completo Reactivo

### 1. onForkEntered(ForkRailTrack)
Se llama reactivamente desde `TrainMovementManager` cuando la locomotora (el primer linker) entra físicamente en un desvío.

```
onForkEntered(fork)
  ├── 1. Transicionar al segmento objetivo (nextSegment)
  ├── 2. Failsafe: Asegurar propiedad del segmento actual (tryLock si no lo posee)
  │      └── Si falla → Parada de emergencia (invasión)
  └── 3. Pre-bloquear el siguiente segmento
         ├── findNextSegment(head, graph)
         └── tryLock(nextSegment)
             ├── Éxito → isWaitingForBlock = false
             └── FALLO → tryAlternativeSegment()
                 ├── Éxito → usa segmento alternativo
                 └── FALLO → isWaitingForBlock = true, frenar/parar
```

### 2. onForkExited(ForkRailTrack)
Se llama reactivamente desde `TrainMovementManager` cuando el último vagón (cola del tren) sale completamente de un desvío. Libera de forma inmediata y determinista el segmento despejado.

```
onForkExited(fork)
  ├── 1. Obtener RailNode asociado al desvío
  ├── 2. Obtener el exitPort de salida del nodo
  ├── 3. Determinar el puerto despejado por el estado físico de la aguja (TRUNK, A o B)
  ├── 4. Obtener el clearedSegment de dicho puerto
  └── 5. Liberar el segmento clearedSegment en el BlockManager
```

### 3. hasPermissionToMove()
```
¿Manual? → true
¿Auto?
  ├── ¿head o track null? → false
  ├── ¿isWaitingForBlock? → true solo si speed > 0 (inercia restante)
  └── → true
```

### 4. acquireInitialLocks()
Se llama al arrancar desde cero (velocidad objetivo > 0 desde 0).
```
  ├── Obtener currentSegment del head track
  ├── tryLock(currentSegment) → si falla → forceEmergencyStop
  └── findNextSegment + tryLock(nextSegment)
      └── si falla → brake (si AUTO)
```

### 5. onBlockReleased()
Callback de BlockManager cuando otro tren libera un segmento.
```
¿AutoMode && isWaitingForBlock && nextSegment != null?
  └── tryLock(nextSegment)
      ├── Éxito → isWaitingForBlock = false, restoreSpeed()
      └── FALLO → tryAlternativeSegment()
```

### 6. onReverse()
```
release(nextSegment) si existe y es distinto del actual
findNextSegment + tryLock (misma lógica que acquireInitialLocks)
```

### 7. claimOccupiedSegments()
Reclama los bloques físicamente ocupados durante la inicialización (Tabula Rasa) o al cargar partida.
```
releaseAll(train)
Recoger segmentos ocupados por los linkers
tryLock cada uno → si falla → tryShuntingLock
  └── si shunting también falla → forceEmergencyStop
```

### 8. findNextSegment(head, graph)
```
¿Reentrada? → findNextSegmentTopological
¿Autopilot en FOLLOWING/WAITING?
  ├── Buscar currentSegment en la ruta
  ├── ¿Siguiente en ruta existe?
  │   ├── ¿Coincide con el topológico? → Usar el de la ruta
  │   └── NO COINCIDE:
  │       ├── ¿Aguja (fork) ocupada? → usar topológico (failsafe)
  │       └── ¿Aguja (fork) libre? → Usar el de la ruta (el piloto la alineará a tiempo)
  └── → findNextSegmentTopological
```

### 9. findNextSegmentTopological(head, graph)
```
Obtener headTrack (RailTrack) y su segmento actual
exitDir = head.getRealDir()
RailIterator(headTrack, exitDir) → avanzar por vías físicas
  └── ¿Encontramos un RailTrack con segmento distinto? → devolverlo
  └── ¿Bucle infinito? → max 10000 iteraciones
  └── ¿Fin de vía? → null
```

---

## Diagrama de Flujo (entrada a nuevo segmento)

```
MovementManager.moveLinkers()
  → safetyManager.onForkEntered(fork)
      │
      ├── currentSegment = nextSegment
      ├── isWaitingForBlock = false
      │
      ├── Failsafe: tryLock(currentSegment)
      │   ├── OK ──────────────┐
      │   └── FAIL → forceEmergencyStop() ──┐
      │                                     │
      ├── PASO posterior: findNextSegment() │
      │   ├── null o igual → skip           │
      │   ├── tryLock(next)                 │
      │   │   ├── OK ────────┐              │
      │   │   └── FAIL → brake() ───┐       │
      │   └── tryAlternative()      │       │
      │       ├── OK ─────┐         │       │
      │       └── FAIL ───┘         │       │
      │                             │       │
      ▼                             ▼       ▼
  Sigue moviéndose                Tren parado
  (nextSegment reservado)         (esperando onBlockReleased)
```

---
*Última actualización: 2026-06-09*
