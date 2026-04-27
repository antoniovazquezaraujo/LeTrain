# Mejores Prácticas: LibGDX y Lanterna

## 1. LibGDX (Renderizado 3D)
- **Separación de Lógica y Vista**: La lógica de simulación (economía, rutas) NUNCA debe depender de `com.badlogicgames.gdx`.
- **Frustum Culling**: Es obligatorio utilizar el `Camera` de LibGDX para realizar culling manual en objetos fuera de la vista para mantener el rendimiento.
- **Gestión de Assets**: Utilizar `AssetManager` para cargar texturas, modelos y fuentes. No cargar assets directamente en el render loop.
- **Ciclo de vida**: Respetar `create()`, `render()`, `resize()` y `dispose()`. Toda implementación de renderizado debe implementarse como un `Visitor` (ver `patterns.md`).

## 2. Lanterna (Terminal UI)
- **Abstracción**: No usar `Screen` o `TextGraphics` directamente en el dominio. Crear una capa de abstracción `TerminalView` que sea implementada por la UI de consola.
- **Performance**: Las operaciones de terminal son costosas. Utilizar `refresh()` solo cuando sea necesario en lugar de redibujar todo el buffer en cada frame.
- **Layouts**: Preferir el uso de los componentes `Panel` y `Layout` de Lanterna en lugar de coordenadas manuales para soportar diferentes tamaños de terminal.

## 3. Integración Cross-UI (MVP)
- **Presenter**: El `Presenter` actúa como intermediario. Recibe eventos del `View` (clic en 3D o tecla en terminal) y actualiza el `Model`.
- **Interfaces de Vista**: El `Presenter` solo debe conocer las interfaces de la vista (ej. `GameViewListener`), permitiendo que el sistema funcione indistintamente con el motor 3D o la terminal.
