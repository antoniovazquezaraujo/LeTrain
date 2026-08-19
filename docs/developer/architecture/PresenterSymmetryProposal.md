# Propuesta de Simetría en Presentadores y Vistas (MVP)

> [!IMPORTANT]
> **ESTADO: ACEPTADO E IMPLEMENTADO**
> Ver [[adr/ADR-006-Symmetry-in-Presenters|ADR-006]] para los detalles finales de la implementación.

## Estado Actual (Pre-Refactor)
El proyecto cuenta con dos implementaciones del patrón MVP, pero con nombres y estructuras inconsistentes:

| Característica | Implementación Terminal (2D) | Implementación LibGDX (3D) |
| :--- | :--- | :--- |
| **Paquete** | `letrain.mvp.impl.terminal` | `letrain.mvp.impl.gdx3d` |
| **Clase Principal** | `CompactPresenter` | `Gdx3DView` |
| **Wrappers** | `Presenter2D` (extiende `CompactPresenter`) | `Presenter3D` (extiende `Gdx3DView`) |
| **Relación P/V** | Presentador y Vista separados (`TerminalView`) | Presentador y Vista unidos en `Gdx3DView` |

## Problemas Identificados
1.  **Nombres Opacos**: `CompactPresenter` no indica que es para terminal. `Gdx3DView` se llama "View" pero actúa como "Presenter".
2.  **Wrappers Redundantes**: `Presenter2D` y `Presenter3D` solo existen para dar nombres "bonitos" pero añaden una capa de herencia innecesaria.
3.  **Inconsistencia de Paquetes**: `gdx3d` hace referencia a la tecnología, mientras que `terminal` hace referencia al tipo de interfaz.

## Propuesta de Nueva Estructura

### 1. Nomenclatura de Clases
Propongo usar nombres que describan la **naturaleza** de la interfaz, no solo la tecnología:

*   **TerminalPresenter**: El presentador para interfaces basadas en texto (Lanterna).
*   **GraphicPresenter**: El presentador para interfaces gráficas aceleradas (LibGDX).

### 2. Estructura de Paquetes
Alinear los paquetes para que sean simétricos:

```
letrain.mvp.impl
├── terminal
│   ├── TerminalPresenter.java (antes CompactPresenter)
│   └── TerminalView.java
└── graphic
    ├── GraphicPresenter.java (antes Gdx3DView)
    ├── Gdx3DHud.java
    └── ... (otros componentes de GDX)
```

### 3. Eliminación de Wrappers
Eliminar `Presenter2D` y `Presenter3D`. En `LeTrain.java`, instanciaremos directamente las clases base:
```java
if (use3D) {
    GraphicPresenter presenter = new GraphicPresenter(model);
} else {
    TerminalPresenter presenter = new TerminalPresenter(model);
}
```

### 4. Separación de Responsabilidades en GraphicPresenter
A largo plazo, deberíamos intentar que `GraphicPresenter` no sea un `ApplicationAdapter` directamente, sino que delegue la vista LibGDX a otra clase, igual que hace el presentador de Terminal. Sin embargo, por ahora, el cambio de nombre a `GraphicPresenter` ya clarifica que esa clase **contiene la lógica de presentación**.

---
**¿Qué opinas de este enfoque, Maquinista?** Si te gusta, podemos usarlo como guía para el refactor.
