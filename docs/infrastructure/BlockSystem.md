[[Index|⬅️ Volver al Índice]]

# Seguridad y Colisiones (Simple Blocking)

Actualmente, LeTrain utiliza un sistema de seguridad basado en el chequeo de ocupación baldosa a baldosa en lugar de un gestor de cantones (`BlockManager`) centralizado.

## Mecanismo de Colisión
La seguridad se gestiona durante el movimiento del tren en `Train#moveLinkers(boolean)`.

1. **Chequeo de Ocupación**: Antes de que cada pieza del tren (`Linker`) avance a la siguiente baldosa (`RailTrack`), el sistema verifica si esa baldosa ya tiene un `Linker` asignado:
   ```java
   Linker occupyingL = nextTrackOfLinker.getLinker();
   ```
2. **Detección de Conflicto**:
   - Si la baldosa está ocupada por un tren diferente, se produce una interacción física.
   - Si la velocidad es alta (`v >= 5`), se dispara un **Choque (`crash`)**.
   - Si la velocidad es baja, se produce un **Contacto** y el tren se detiene inmediatamente.
3. **Sensores y Semáforos**: 
   - Los trenes activan sensores al entrar/salir de una baldosa.
   - Estos sensores pueden disparar programas de automatización (ANTLR) que a su vez cierran semáforos para detener otros trenes.

## Arquitectura Objetivo (ADR-005)
Existe una propuesta de diseño para implementar un sistema de **Segmentos Atómicos** que sustituya al chequeo baldosa a baldosa por una reserva de tramos completos de vía entre estaciones y desvíos. 
Ver [[adr/ADR-005-Block-Segments|ADR-005: Sistema de Seguridad por Segmentos]] para más detalles sobre esta futura implementación.

## Símbolos Clave
- `letrain.vehicle.impl.rail.Train`: Implementa el método `moveLinkers` con la lógica de colisión.
- `letrain.track.rail.RailTrack`: Mantiene la referencia al `Linker` que la ocupa actualmente.
- `letrain.track.RailSemaphore`: Objeto de vía que puede ser consultado por la automatización para gestionar el tráfico.
