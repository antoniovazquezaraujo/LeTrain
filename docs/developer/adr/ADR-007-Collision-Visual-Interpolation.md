# ADR-007: Interpolación visual durante colisiones a baja velocidad

## Estado
ACEPTADO (2026-05-09)

## Contexto
Al colisionar dos trenes a velocidad baja (1-4), la locomotora se detenía correctamente por lógica de física, pero los vagones se deslizaban visualmente dentro de la locomotora como si fuera transparente, para luego volver a su posición correcta. En marcha atrás, la locomotora se insertaba en los vagones. Además, el sonido de choque se reproducía con ~2.5 segundos de retraso.

## Causas identificadas

### 1. Direcciones sucias tras movimiento fallido
`Train.advance()` llamaba a `setDirTowedLinkers()` antes de `moveLinkers()`. Si éste fallaba por colisión, las direcciones (`dir`, `entryDir`) de los vagones quedaban modificadas apuntando hacia la locomotora, y el renderer las usaba para calcular una posición visual incorrecta.

### 2. El renderer no verificaba el bloqueo
`VehicleRenderer.visitWagon()` y `visitLocomotive()` llamaban a `PathGeometry.calculateTwoStagePath()` sin comprobar si la celda destino estaba ocupada por otro tren. La interpolación visual metía los vehículos en celdas bloqueadas.

### 3. Lookahead insuficiente en cadenas de vagones
La comprobación original solo miraba una celda hacia adelante. Con varios vagones (`Wagon2 → Wagon1 → Loco → TrenB`), Wagon2 veía a Wagon1 (mismo tren) y asumía que podía avanzar, sin saber que la locomotora estaba bloqueada más adelante.

### 4. Sonido de choque retardado
La colisión se detectaba en `moveLinkers()` → `advance()`, que solo se ejecuta cuando `turns == 0` (~50 ticks a velocidad 1). Tras mover el tren a la celda adyacente al obstáculo, el siguiente `advance()` tardaba 50 ticks en ejecutarse y detectar la colisión.

## Decisión

### Capa 1: Salvar y restaurar direcciones
En `Train.advance()`, guardar `dir` y `entryDir` de todos los linkers antes de `setDirTowedLinkers()`. Si `moveLinkers()` retorna `false`, restaurar las direcciones originales.

### Capa 2: Parámetro `canEnterNext` en `calculateTwoStagePath`
Añadir un parámetro booleano `canEnterNext` a `PathGeometry.calculateTwoStagePath()`. Cuando es `false`:
- **Phase 1**: `t = 0.5f` (centro de la celda actual, sin desplazamiento)
- **Phase 2**: no se ejecuta (se queda en centro de celda actual)

En `VehicleRenderer`, calcular `canEnterNext` antes de llamar a `calculateTwoStagePath()`.

### Capa 3: Lookahead encadenado
En lugar de mirar solo la celda inmediata, recorrer toda la cadena de linkers del mismo tren hacia adelante hasta encontrar:
- **Celda libre** (`occupant == null`) → `canEnterNext = true`
- **Otro tren** (`occupant.getTrain() != train`) → `canEnterNext = false`
- **Mismo tren** → seguir la cadena (`lookDir = occupant.getDir()`)

Aplicado simétricamente en `visitLocomotive()` y `visitWagon()`.

### Capa 4: Bypass total cuando `speed == 0`
Cuando la velocidad es 0 (tren parado), no se llama a `calculateTwoStagePath()` en absoluto: el vehículo se dibuja directamente en el centro de su celda (`getPosition().getX() + 0.5f`).

### Capa 5: Detección de colisión inmediata post-movimiento
Al finalizar `moveLinkers()` con éxito, verificar **inmediatamente** si la siguiente celda está ocupada por otro tren. Si es así, disparar `notifyContact()` sin esperar al siguiente ciclo de `advance()`.

## Consecuencias
- Los vagones (y locomotoras en marcha atrás) ya no se deslizan visualmente dentro de otros vehículos durante colisiones a baja velocidad.
- El sonido de choque se reproduce instantáneamente al alcanzar la celda adyacente al obstáculo.
- La interpolación visual durante el movimiento normal no se ve afectada.
- Se añade el método `canEnterNextTrack()` a `PathGeometry` como punto único de verificación.
- Se añade guarda en `Locomotive.incSpeed()` para no aumentar velocidad mientras el tren está stalled.

## Archivos modificados
| Archivo | Cambio |
|---------|--------|
| `VehicleRenderer.java` | Lookahead encadenado + bypass `speed==0` en ambos métodos |
| `PathGeometry.java` | `calculateTwoStagePath(..., boolean canEnterNext, ...)` |
| `CameraController.java` | Misma lógica de `canEnterNext` |
| `Train.java` | Salva/restaura direcciones + chequeo post-movimiento |
| `Locomotive.java` | `incSpeed()` bloqueado si tren stalled |
