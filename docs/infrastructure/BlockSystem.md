[[Index|⬅️ Volver al Índice]]

# Seguridad y Colisiones (Segment Blocking)

LeTrain ha evolucionado de un chequeo baldosa a baldosa a un sistema de **Segmentos Atómicos** (basado en el [[adr/ADR-005-Block-Segments|ADR-005]]), gestionado por el `BlockManager`.

## El Sistema de Segmentos
La red ferroviaria se divide lógicamente en segmentos indivisibles cuyos límites son los **Nodos** (Forks o DeadEnds).

1. **Propiedad y Reserva**: Un tren debe poseer el segmento que ocupa físicamente y reservar el segmento siguiente antes de entrar en él.
2. **Cascada de Seguridad**: En cada avance, el tren utiliza un mecanismo de "look-ahead" para verificar la viabilidad de su ruta futura.
3. **Frenado Proactivo**: Si el tren no puede obtener la propiedad del siguiente segmento (porque está ocupado por otro tren o un desvío está mal orientado), inicia un frenado de emergencia.

## El Rol de `RailIterator`
Para que el sistema de segmentos funcione, el tren necesita "ver" más allá de su posición actual. Aquí es donde entra el `RailIterator`:
- **Exploración Lógica**: El iterador recorre las vías por delante del tren para identificar dónde termina el segmento actual y qué segmento sigue.
- **Robustez de 45 Grados**: El iterador está diseñado para manejar la geometría de LeTrain, incluyendo curvas de 45 grados y conexiones desalineadas (kinks), asegurando que el sistema de seguridad no se quede "ciego" en tramos complejos como apartaderos.

## Mecanismo de Colisión Física
A pesar del bloqueo lógico, se mantiene una capa de seguridad física en `Train#moveLinkers(boolean)` como última línea de defensa:
- **Detección Directa**: Verifica la ocupación física de la baldosa destino.
- **Consecuencias**: Velocidad alta resulta en **Choque (`crash`)**, velocidad baja en **Parada Inmediata**.

## Símbolos Clave
- `letrain.core.segments.BlockManager`: Gestor central de la propiedad de los segmentos.
- `letrain.vehicle.impl.RailIterator`: Herramienta de exploración para la lógica de bloques.
- `letrain.core.segments.RailwayGraph`: Representación topológica de la red en segmentos.
