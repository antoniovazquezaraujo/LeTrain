# Mejores Prácticas Técnicas (UI)

## 1. Lanterna (Terminal)
- **Modo Screen**: Siempre preferir `TerminalScreen` para gestión de frames.
- **Optimización**: No redibujar el fondo en cada tick. Usar `TextGraphics` para modificar solo los caracteres necesarios (dirty rectangles).
- **Entrada**: Implementar `KeyStrokeListener` de forma asíncrona para no bloquear el hilo de renderizado principal.

## 2. LibGDX (3D UI)
- **HUD (Heads-Up Display)**: Utilizar `Stage` y `Table` (Scene2D) para crear interfaces 2D que se rendericen sobre el mundo 3D.
- **Batching**: Usar `SpriteBatch` solo para elementos 2D críticos. Agrupar llamadas de renderizado para reducir drásticamente el impacto en GPU.
- **Performance**:
    - Usar `TextureAtlas` para minimizar el cambio de texturas en el render loop.
    - Implementar LOD (Level of Detail) para elementos de UI lejanos si fuera necesario.

## 3. Desacoplamiento (MVP)
- La vista (Lanterna/LibGDX) solo emite eventos (clics, teclas).
- El `Presenter` escucha esos eventos y decide qué pasa en el `Model`.
- La vista NUNCA modifica el `Model` directamente.
