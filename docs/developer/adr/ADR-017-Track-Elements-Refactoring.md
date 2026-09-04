# ADR-017: Refactorización de Elementos de Vía (Track Elements)

## Contexto
Actualmente, la arquitectura de la clase `RailTrack` y los elementos que pueden existir en ella (como `Sensor`, `RailSemaphore`, `SpeedSignal`, `Station`, y `ForkRailTrack`) está muy acoplada. Esto genera varios problemas ("está todo bastante liado"):
- Gran cantidad de validaciones `instanceof`.
- Responsabilidades mezcladas (lógica de negocio vs renderizado vs reglas de colisión).
- Difícil de extender si queremos añadir nuevos elementos (ej. pasos a nivel, desvíos triples).
- Posible abuso de la herencia en lugar de la composición.

## Objetivo del Análisis
El objetivo es realizar una radiografía detallada del estado actual y proponer una arquitectura más limpia (ej. Decorator, ECS, o Composición estricta) antes de escribir código.

## Metodología Propuesta
1. **Fase 1: Mapeo y Diagnóstico**. Analizar las dependencias actuales, relaciones de herencia y puntos de acoplamiento. (A cargo del subagente `research`).
2. **Fase 2: Propuesta de Diseño**. Definir el patrón de diseño objetivo y discutir las ventajas/desventajas.
3. **Fase 3: Refactorización Iterativa**. Ejecutar el cambio en múltiples PRs atómicas sin romper los tests existentes.

## Estado Actual (En progreso)
*(Aquí se añadirán los hallazgos de la Fase 1 tras el análisis)*

