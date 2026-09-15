# ADR-021: Estrategia de Descomposición de Model.java

## Estado: ACEPTADO

## Contexto
La clase `Model.java` en `core/src/main/java/letrain/mvp/impl/Model.java` había crecido hasta superar las 1.900 líneas de código, convirtiéndose en un "God Object" que centralizaba:
1. Almacenamiento de estado de entidades y vías.
2. Motor de simulación y física ferroviaria.
3. Cableado de listeners del sistema y trenes.
4. Lógica de selección y navegación entre entidades (locomotoras, desvíos, estaciones, etc.).
5. Algoritmos de movimiento de elementos sobre vías y cálculo de roles industriales por terreno.
6. Generación de informes textuales de diagnóstico y definición de menús del modo de juego.
7. Ejecución de comandos del lenguaje de automatización (ANTLR).

A su vez, el proyecto cuenta con un sistema de serialización basado en Jackson (`GameSaveService` y `ModelMixin`) que serializa los campos de `Model` directamente mediante inspección reflexiva (`fieldVisibility = ANY`), requiriendo que la representación de los datos guardados en disco (`.json`) permanezca estrictamente compatible.

## Decisión
Se adopta una estrategia de descomposición en dos fases:

### Fase 1: Descomposición Funcional mediante Servicios de Dominio Sin Estado (Implementada)
Se extrajo la lógica procedimental de `Model.java` a servicios especializados dentro de `letrain.mvp.impl.services`:
- `ModelSelectionService`: selección y ciclado de entidades del juego.
- `TrackElementMovementService`: desplazamiento de sensores y estaciones a lo largo de la topología de vías y detección de industrias.
- `ModelReportService`: generación del modelo de menú e informes (`getGameObjectsReport`, `getRailwayGraphReport`).
- `ModelListenerService`: configuración y cableado de escuchadores del sistema.
- `SimulationService` y `AutomationEngine`: simulación física y ejecución de scripts.

**¿Por qué métodos estáticos / servicios sin estado en Fase 1 en lugar de inyección de dependencias con interfaces?**
1. **Compatibilidad estricta con serialización Jackson:** Los campos de estado deben permanecer en la raíz del `Model` para no invalidar las partidas guardadas existentes ni los checkpoints del diario de comandos (`ADR-020`). Inyectar servicios con referencias bidireccionales en campos de instancia introduciría ciclos de serialización o exigiría anotaciones `@JsonIgnore` / mixins adicionales.
2. **Cero coste de memoria y snapshot limpio:** Los servicios estáticos no consumen memoria adicional al instanciar modelos ni al clonar checkpoints de estado para el sistema de `undo/redo`.
3. **Estabilidad del contrato público:** La interfaz `letrain.mvp.Model` se mantiene inalterada, evitando cambios en cascada en los presentadores de la UI 2D (Lanterna) y 3D (LibGDX).

### Fase 2: Evolución Futura hacia Inyección e Interfaces
A futuro, si se requiere desacoplar completamente `Model` de la topología o soportar múltiples estrategias de reportes o movimiento:
1. Extraer interfaces formales (p. ej. `SelectionManager`, `ReportGenerator`).
2. Diseñar un `GameState` puro (datos desacoplados de la lógica) que encapsule los campos serializables, permitiendo que `Model` sea un agregador de servicios inyectados.
