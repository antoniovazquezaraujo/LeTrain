# ADR-006: Simetría en Presentadores y Vistas (MVP)

## Estado
Aceptado (Implementado)

## Contexto
El proyecto contaba con dos implementaciones del patrón MVP con nombres inconsistentes y estructuras asimétricas:
- Terminal: `CompactPresenter` (en `letrain.mvp.impl.terminal`) envuelto en `Presenter2D`.
- LibGDX: `Gdx3DView` (en `letrain.mvp.impl.gdx3d`) envuelto en `Presenter3D`.

Esta asimetría dificultaba la navegación del código y generaba confusión sobre qué clases contenían la lógica de presentación real.

## Decisión
Se ha procedido a una refactorización profunda para unificar la nomenclatura y estructura:
1. **Renombrado de Clases**:
   - `CompactPresenter` -> `TerminalPresenter`
   - `Gdx3DView` -> `GraphicPresenter`
2. **Reestructuración de Paquetes**:
   - `letrain.mvp.impl.terminal` (se mantiene)
   - `letrain.mvp.impl.gdx3d` -> `letrain.mvp.impl.graphic`
3. **Eliminación de Wrappers**:
   - Se han eliminado `Presenter2D` y `Presenter3D`.
   - `LeTrain.java` instancia ahora directamente `TerminalPresenter` o `GraphicPresenter`.
4. **Simetría de Vistas**:
   - `TerminalPresenter` usa `TerminalView`.
   - `GraphicPresenter` gestiona su propia vista (LibGDX) pero se ha clarificado su rol como presentador.

## Consecuencias
- **Mejor legibilidad**: Los nombres ahora reflejan la naturaleza de la interfaz (Terminal vs Gráfica) en lugar de solo la tecnología.
- **Simplificación**: Menos niveles de herencia y abstracciones innecesarias.
- **Consistencia**: Una estructura de paquetes predecible para ambas implementaciones.
