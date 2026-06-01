# Plan de Implementación — Búsqueda de Vía Alternativa (Siding / Ramal Paralelo)

Este plan describe la solución para evitar bloqueos y mejorar la fluidez de tráfico en la red ferroviaria mediante la búsqueda y reserva reactiva de un ramal alternativo directo antes de detener el tren.

## User Review Required

> [!IMPORTANT]
> - **Búsqueda e Intento de Reserva Previos al Frenado**: Antes de invocar a `initiateBraking()`, si el segmento destino planificado (`nextSegment`) está bloqueado, el sistema buscará un segmento alternativo directo (`sAlt`).
> - **Criterios de Alternativa Directa**: 
>   1. El ramal alternativo debe conectar exactamente a los mismos dos nodos (por ejemplo, desvíos) que el segmento original.
>   2. El segmento original que se va a sustituir no debe contener ningún waypoint pendiente en el itinerario.
>   3. El ramal alternativo debe estar libre (bloqueable por el tren).
> - **Actualización del Piloto Automático**: Si se encuentra y reserva con éxito la vía alternativa:
>   - Se actualiza el `nextSegment` en el gestor de seguridad.
>   - Se actualiza la ruta activa (`currentRoute`) del piloto automático para evitar que el tren intente volver al segmento bloqueado.
>   - Se orientan correctamente las agujas del desvío para la nueva vía.

## Proposed Changes

### Componente: Control de Cantón e Itinerario (`letrain.itinerary` y `letrain.vehicle.rail.impl`)

---

#### [MODIFY] [AutoPilot.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/AutoPilot.java)
- Añadir la declaración del método `void replaceRouteSegment(Segment oldSeg, Segment newSeg);` para permitir la sustitución dinámica de un segmento en el itinerario/ruta del piloto automático.

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- Implementar `replaceRouteSegment(Segment oldSeg, Segment newSeg)`:
  - Hacer una copia mutable de `currentRoute`.
  - Buscar la posición de `oldSeg`.
  - Si se encuentra, sustituirla por `newSeg`.
  - Actualizar `currentRoute` con una versión inmutable.
  - Asegurar la orientación correcta del desvío/agujas llamando a `ensureForkRoute` para el segmento modificado y sus colindantes en la ruta.

#### [MODIFY] [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java)
- Implementar el método privado `boolean tryAlternativeSegment(Model model)`:
  - Validar si el tren tiene piloto automático activo y un `nextSegment` definido.
  - Comprobar que en `nextSegment` no haya waypoints pendientes (comparando con `waypoints` de la ruta actual a partir de `currentIndex()`).
  - Buscar en la topología (usando `RailwayGraph`) un segmento alternativo directo (`sAlt`) que comparta los dos mismos extremos (`RailNode`) que `nextSegment`.
  - Intentar bloquearlo (`bm.tryLock(train, sAlt)`).
  - Si es exitoso, actualizar `nextSegment` y notificar al piloto automático mediante `ap.replaceRouteSegment(oldNext, sAlt)`.
- Modificar las secciones de reserva de bloque (`bm.tryLock(train, nextSegment)`) en `acquireInitialLocks`, `onSegmentEntered` y `onBlockReleased`:
  - Si `tryLock` sobre `nextSegment` falla, invocar a `tryAlternativeSegment(model)` para buscar e intentar bloquear el ramal alternativo antes de proceder a la detención/frenado.

---

## Verification Plan

### Automated Tests
- Ejecutar la suite completa de pruebas:
  ```bash
  mvn clean test
  ```

### Manual Verification
- Cargar la partida provista para ver si el tren evita quedarse bloqueado ante un desvío ocupado tomando el ramal paralelo alternativo y libre.
