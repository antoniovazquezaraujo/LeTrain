# Editor de programación (2D y 3D)

Estado: implementado en #828 (Programming v2).
Referencia de modelo: [[ADR-031-Programming-Quickref-Model]].
Lógica de texto compartida: `letrain.command.ProgramTextEdit`.

## Quick reference

- Solo los nodos `COMMAND` se insertan. `HEADING` y `GROUP` son estructura; `INFO` es una nota
  seleccionable pero **inerte** (nunca inserta código).
- Cada `COMMAND` muestra su scope **solo en la pestaña Programming**:
  - `[top]`: solo primer nivel (`BODY_ONLY`).
  - `[in {}]`: solo dentro de un bloque de trigger/tiempo (`BLOCK_ONLY`).
  - sin etiqueta: válido en ambos (`BOTH`).
- En **Scenario** (`on build` / `on start`) y **Config** no se aplica ni se muestra el scope de
  PROGRAM: ambos clientes pasan `null` a `ProgramTextEdit.isInsertionAllowed` mediante
  `ProgramTextEdit.scopeForTab(programmingTab, scope)`, así que se puede insertar dentro de los
  bloques de la receta sin avisos.
- La lista/árbol se puede recorrer con el teclado; el panel **PREVIEW** de debajo muestra el
  snippet exacto y su scope, o la prosa de un `INFO`.
- El editor de Programming guarda el texto dentro de `program { … }`; el scope se mide con
  `ProgramTextEdit.programBodyDepth`, que descuenta ese wrapper (cuerpo = 0, trigger = 1). Un
  cuerpo crudo sin wrapper conserva su profundidad normal.
- Si el caret está en el scope equivocado, la inserción se bloquea y el estado muestra el aviso
  (“only works at the top level of the program” / “only works inside a trigger or time block”).

## Mapa de teclas

| Contexto | Tecla | Acción |
|---|---|---|
| Editor | `Tab` | Inserta dos espacios (o pasa al siguiente placeholder) |
| Editor | `Shift+Tab` | Dedent de 2 espacios (o placeholder anterior) |
| Editor | `Enter` | Nueva línea con la indentación actual; +2 tras `{`; outdent antes de `}` |
| Editor | escribir | Si hay placeholder activo, sustituye el token completo |
| Editor | `Backspace`/`Delete` | Borra el placeholder activo completo |
| Editor | `Ctrl+Tab` / `Ctrl+Shift+Tab` | Siguiente/anterior foco |
| Editor | `F6` / `Shift+F6` | Fallback de foco (terminales que no distinguen Ctrl+Tab) |
| Editor | `Alt+Q` | Ir a la quick reference |
| Quick reference | `↑`/`↓` | Mover selección |
| Quick reference | `←`/`→` | Colapsar/expandir grupo |
| Quick reference | `Enter` | Insertar `COMMAND`; plegar `GROUP`; inerte en `INFO` |
| Ventana | `Alt+A/G/F`, `Alt+1/2/3` | Cambiar de pestaña |
| Ventana | `Alt+E/I/R/P/B/C` | Export/Import/Reload/Apply/Rebuild/Close |
| Ventana | `Esc` | Cerrar el editor |

Los atajos y las etiquetas se comparten desde `EditorText`; la línea de estado muestra el mapa
resumido en ambos clientes.

## Inserción

`ProgramTextEdit.insert` es la única implementación de las reglas:

- Línea vacía: inserta en esa línea con su indentación.
- Línea con contenido: inserta en una línea nueva debajo, con la indentación de la línea actual.
- Snippet multilínea: prefija la indentación base a todas las líneas y conserva la indentación
  relativa.
- Tras insertar, se activa el primer `<…>`:
  - 3D: `TextArea.setSelection` nativo; `Tab`/`Shift+Tab` ciclan con wrap.
  - 2D: Lanterna no tiene selección; se coloca el caret al inicio del token, se recuerda el rango
    y al teclear se sustituye el token completo.
- Sin placeholders: el caret queda al final del snippet.

## Errores

Los diagnósticos conservan **línea y columna** al saltar al editor en ambos clientes. En 3D los
botones de error entran en el ciclo de foco (`Ctrl+Tab`/`F6`) cuando el panel está visible.

## Tests

- `ProgramTextEditTest`: inserción, indentación, dedent, `braceDepth`, placeholders y scope
  (incluye `scopeForTab` y la inserción anidada en Scenario).
- `ProgramTextBoxScopeTest`: la UI 2D inserta dentro de `on build { … }` con scope `null` y sigue
  bloqueando `BODY_ONLY` en Programming.
- `TerminalViewPreviewTest`: preview/wrapping del cliente 2D.
- `Gdx3DHudPreviewTest`: preview del cliente 3D.
- `TerminalViewDiagnosticLocatorTest`: línea:columna al mapear diagnósticos.
