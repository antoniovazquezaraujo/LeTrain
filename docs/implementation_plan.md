# Plan de Implementación — Simplificación de Trailer y Mejora de TrainCouplingManager

Este plan propone limpiar el diseño del acoplamiento y estructura del tren simplificando la jerarquía de interfaces redundantes y aclarando el contrato de acoplamiento.

## Cambios Propuestos

### 1. Eliminar la Interfaz Redundante `Trailer`
La interfaz `Trailer` es un artefacto obsoleto que solo implementa la clase `Train`, y cuyos métodos de mutación (`pushFront`, `popFront`, etc.) no son consumidos fuera de `Train`.
- **Acción**: Eliminar [Trailer.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/Trailer.java).
- **Modificación en Train**:
  - Quitar `implements Trailer<RailTrack>` de [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java).
  - Mantener como métodos públicos estándar en `Train` los métodos realmente útiles (como `getLinkers()`, `isEmpty()`, `size()`, `getFront()`, `getBack()`, `getDirectorLinker()`, `setDirectorLinker()`, `getTractors()`).
  - Eliminar métodos no utilizados e innecesarios de la interfaz `Trailer` (tales como `joinTrailerBack`, `joinTrailerFront`, `pushFront`, `popFront`, `pushBack`, `popBack` que no tienen llamadas externas).

### 2. Aclarar y Documentar la Interfaz `TrainCouplingManager`
El `TrainCouplingManager` maneja la lógica de acoplamiento y desacoplamiento, seleccionando y preparando qué vehículos (linkers) se unirán o separarán (impulsado por la UI o por lógica interna).
- **Acción**: Documentar rigurosamente los métodos en [TrainCouplingManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/TrainCouplingManager.java) con comentarios JavaDoc para clarificar su responsabilidad.
- **Limpieza de getters/setters internos**: Mantener la interfaz limpia y comprensible.

---

## Plan de Verificación

### Pruebas Automatizadas
- Compilar el proyecto y ejecutar los tests existentes para asegurar que no hay regresiones:
  ```bash
  mvn clean test
  ```
