# Plan de Implementación — TrainCouplingManager Stateless

Este plan describe el refactor para hacer que `TrainCouplingManager` sea stateless (sin estado de instancia). El estado efímero del acoplamiento (selecciones de UI) pasará a residir en `Train` como campos transitorios, y el gestor de acoplamiento se convertirá en un servicio puro de lógica ferroviaria.

## Cambios Propuestos

### 1. Modificar la Entidad `Train`
Añadir el estado transitorio del menú de acoplamiento a [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java) y sus correspondientes getters y setters:
- Campos transitorios:
  - `private transient Deque<Linker> linkersToJoin = new LinkedList<>();`
  - `private transient int numLinkersToJoin = 0;`
  - `private transient Deque<Linker> linkersToRemove = new LinkedList<>();`
  - `private transient int numLinkersToRemove = 0;`
  - `private transient LinkersSense linkerJoinSense;`
  - `private transient LinkersSense linkerDivisionSense;`
  - `private transient boolean joined = false;`
- Getters y setters para estos campos para mantener la compatibilidad con los Visitors y Presenters.
- Mantener una referencia estática o compartida a la implementación stateless de `TrainCouplingManager` para llamadas locales rápidas si es necesario, o invocarla directamente.

### 2. Refactorizar la Interfaz `TrainCouplingManager`
Modificar [TrainCouplingManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/TrainCouplingManager.java) para que todas las funciones acepten `Train train` como su primer argumento.

### 3. Refactorizar la Implementación `TrainCouplingManager`
Modificar [TrainCouplingManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainCouplingManager.java) para eliminar sus variables de instancia y adaptar todos sus métodos para que operen sobre el estado del objeto `Train` recibido como parámetro.

### 4. Actualizar Presentadores e Invocadores
Actualizar todas las llamadas en:
- [TerminalPresenter.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/terminal/TerminalPresenter.java)
- [Gdx3DInputHandler.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java)
- [Gdx3DHud.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/graphic/Gdx3DHud.java)
- [VehicleRenderer.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/visitor/gdx3d/VehicleRenderer.java)
- [InfoVisitor.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/visitor/terminal/InfoVisitor.java)
- [RenderVisitor.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/visitor/terminal/RenderVisitor.java)

---

## Plan de Verificación

### Pruebas Automatizadas
- Compilar el proyecto y ejecutar la suite completa de pruebas:
  ```bash
  mvn clean test
  ```
