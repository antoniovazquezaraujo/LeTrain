# ADR-002: Sistema de Seguridad por Enclavamiento de Rutas (OBSOLETO)

> [!CAUTION]
> **ESTE DOCUMENTO ES OBSOLETO.**
> La lógica de bloqueo por puertos individuales y transacciones de micro-reserva descrita aquí ha sido sustituida por el sistema de **Bloqueo por Segmentos Atómicos** definido en el [[ADR-005-Block-Segments|ADR-005]].
>
> Se mantiene exclusivamente por motivos de registro histórico sobre los intentos fallidos de implementación de la Fase 1.
>
> ### Motivos del descarte:
> 1. **Fragilidad Topológica**: Imposibilidad de garantizar la integridad en nodos complejos (Forks).
> 2. **Complejidad de Rollback**: Los fallos en bloqueos parciales generaban estados inconsistentes en la red.
> 3. **Falta de Atomicidad**: La vía física no quedaba protegida como un todo, permitiendo alcances en tramos de un solo sentido.

Para el diseño actual de seguridad, consulte el **ADR-005**.
