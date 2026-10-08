[[Index|⬅️ Volver al Índice]]

# Seguridad y Colisiones (Segment Blocking)

LeTrain ha evolucionado de un chequeo baldosa a baldosa a un sistema de **Segmentos Atómicos** (basado en el [[adr/ADR-005-Block-Segments|ADR-005]]), gestionado por el `BlockManager`.

## El Sistema de Segmentos
La red ferroviaria se divide lógicamente en segmentos indivisibles cuyos límites son los **Nodos** (Forks o DeadEnds).

1. **Propiedad y Reserva**: Un tren debe poseer el segmento que ocupa físicamente y reservar el segmento siguiente antes de entrar en él.
2. **Cascada de Seguridad**: En cada avance, el tren utiliza un mecanismo de "look-ahead" para verificar la viabilidad de su ruta futura.
3. **Frenado Proactivo**: Si el tren no puede obtener la propiedad del siguiente segmento (porque está ocupado por otro tren o un desvío está mal orientado), inicia un frenado de emergencia. Desde la issue #633 el tren rueda hasta el final de su cantón y frena en la última vía antes del nodo (nada de muros artificiales).
4. **Restauración de la velocidad diferida (issue #650)**: la velocidad deseada que la espera deja diferida (una orden de velocidad durante la espera, el crucero capado por la curva de frenado o la velocidad de una `departure` interceptada) **se restaura cuando la espera se resuelve por cualquier camino**: liberación del bloque (`onBlockReleased`), bloqueo directo del siguiente segmento o bloqueo del **alternativo** (la variante paralela) desde `acquireInitialLocks`/`onSegmentEntered`. Las esperas que siguen activas (parada deliberada, retención por horario) no tocan el target.
5. **Liberación al salir del nodo (issue #625)**: un cantón no se libera cuando la cola pisa el fork frontera (compartido por los dos cantones), sino cuando la cola **sale** del fork: mientras el nodo siga ocupado, el cantón permanece bloqueado (`TrainMovementManager` difiere el `onSegmentExited` y lo libera `onForkExited`). Si se liberara al pisar el nodo, un tren que espera al otro lado arrancaría con la vía compartida aún ocupada y dispararía un contacto falso en el cruce.

## El Rol de `RailIterator`
Para que el sistema de segmentos funcione, el tren necesita "ver" más allá de su posición actual. Aquí es donde entra el `RailIterator`:
- **Exploración Lógica**: El iterador recorre las vías por delante del tren para identificar dónde termina el segmento actual y qué segmento sigue.
- **Robustez de 45 Grados**: El iterador está diseñado para manejar la geometría de LeTrain, incluyendo curvas de 45 grados y conexiones desalineadas (kinks), asegurando que el sistema de seguridad no se quede "ciego" en tramos complejos como apartaderos.

## Mecanismo de Colisión Física
A pesar del bloqueo lógico, se mantiene una capa de seguridad física en `Train#moveLinkers(boolean)` como última línea de defensa:
- **Detección Directa**: Verifica la ocupación física de la baldosa destino.
- **Consecuencias**: Velocidad alta resulta en **Choque (`crash`)**, velocidad baja en **Parada Inmediata**.

## Símbolos Clave
- `letrain.segments.BlockManager`: Gestor central de la propiedad de los segmentos.
- `letrain.vehicle.rail.RailIterator`: Herramienta de exploración para la lógica de bloques.
- `letrain.segments.RailwayGraph`: Representación topológica de la red en segmentos.
