name: ui-master-craftsman
description: Skill de maestría en diseño de interfaces para terminal (Lanterna) y 3D (LibGDX). Úsala para asegurar consistencia visual, rendimiento de renderizado y una UX moderna y pulida.

# UI Master Craftsman

Esta skill proporciona conocimiento experto sobre:

1. **Terminal UI (Lanterna)**: Patrones de diseño para interfaces de consola, gestión de eventos de teclado/ratón y optimización del buffer de pantalla.
2. **3D UI/Rendering (LibGDX)**: Implementación de HUDs, diseño de cámaras, uso eficiente de fuentes y shaders para una estética "procedural" moderna.
3. **Coherencia Visual**: Directrices para mantener la identidad del proyecto tanto en 2D como en 3D.

## Cómo utilizar esta Skill

### 1. Análisis de UX/UI
Cuando diseñes o refactorices una pantalla, consulta [UI_GUIDELINES.md](references/ui-guidelines.md) para asegurar que el diseño respeta la jerarquía visual y la usabilidad.

### 2. Implementación Técnica
Usa [TECHNICAL_BEST_PRACTICES.md](references/technical-best-practices.md) para elegir la mejor implementación (Lanterna vs. LibGDX) y asegurar que el código sea eficiente y desacoplado (MVP).

### 3. Checklist de Calidad Visual
Antes de finalizar cualquier tarea, Jorge debe responder:
- ¿Es la interfaz intuitiva para el usuario final?
- ¿Cumple con los estándares de rendimiento (evitando redibujos innecesarios en consola / usando frustum culling en 3D)?
- ¿Se siente "moderna y viva" (espaciado, tipografía, feedback interactivo)?
