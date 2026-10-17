# Settings en partida (2D y 3D) — S3b #764, S4b #778

Documentación técnica del diálogo **Settings** en partida, abierto desde **File → Settings** mientras
hay una partida en curso (D9/D10, concept §5.2). Es la rebanada S3b del arco **New Game + Settings**
(#742) para el cliente 2D (terminal Lanterna) y la S4b (#778) para el 3D (HUD LibGDX); ambas consumen
las APIs de S3a (aplicación por estado, candado de escenario, escritura de `letrain.cfg`) y las
mismas palabras (`SettingsText`).

## Piezas

| Pieza | Módulo | Responsabilidad |
| :--- | :--- | :--- |
| `letrain.mvp.impl.terminal.SettingsDialog` | ui-terminal | Diálogo Lanterna 2D: pestañas con estados por campo, acciones y feedback. Reutiliza las piezas del formulario de `NewGameDialog`. |
| `letrain.mvp.impl.terminal.DialogWindow` | ui-terminal | Chrome compartido con Nueva Partida: caja centrada/acotada, marco con título centrado y fondo con la instantánea del mapa (#742 polish). |
| `Gdx3DHud.showSettingsDialog(...)` | ui-graphic | Diálogo scene2d 3D: selector de categorías, filas por campo con glifo de estado, leyenda y acciones. |
| `letrain.mvp.SettingsText` | core | Textos compartidos (título, botones, leyenda, confirmaciones, mensajes). |
| `letrain.settings.SettingFieldState` + `SettingsCatalog.fieldState/overDefaults/changedReapplyKeys` | core | Estado por campo (candado de escenario incluido), baseline y detección de REAPPLY compartidos por 2D y 3D. |
| `Model.applyLiveSettings(Map)` | core | Aplica LIVE/REAPPLY a la partida y devuelve `SettingsApplyResult(applied, rejections)` (S3a). |
| `Model.getScenarioSettingKeys()` | core | Claves fijadas por el `configuration { }` del escenario (candado ⚪, D10). |
| `ConfigFileWriter.writeDefaultsOrMessage(File, Map)` | core | Guarda los valores del formulario en el `letrain.cfg` del juego (D4); lo crea si no existe y devuelve el error como mensaje. |
| `EconomyManager.effectiveConfig()` | core | Valores efectivos actuales: base de Restore y de la precarga. |
| `GameViewListener.onSettings()` | core | Puerto File → Settings (los clientes sin diálogo mantienen el no-op). |

## Flujo

```
File → Settings (solo con partida; en portada deshabilitado con "no game in progress")
   --> SettingsDialog.show(valores efectivos + claves de escenario + SettingsPort)
         Apply            --> Model.applyLiveSettings (S3a); rechazos con motivo, sin cerrar
         Restore          --> vuelve a los valores efectivos actuales (baseline)
         New game         --> Action.StartGame(form) --> onNewGame(prefill) [confirmación + diálogo S2]
         Save as defaults --> confirmación + ConfigFileWriter.writeDefaults
         Close / Esc      --> Action.Close
```

- El presentador (`TerminalPresenter.onSettings()`) compone los valores efectivos con la semilla
  del mundo (`Model.getSeed()`) y pasa un `SettingsDialog.SettingsPort` anónimo que delega en
  `applyLiveSettings` y en `ConfigFileWriter` (con `getConfigFile()`, o `letrain.cfg` en el
  directorio de trabajo cuando el juego no encontró ninguno).
- `Action.StartGame` cierra el diálogo y reentra en el flujo de Nueva Partida: confirmación simple
  por partida en curso (como New) y formulario S2 **pre-rellenado** con los valores del formulario.

## Estados por campo

| Marca | Estado | Editable | Notas |
| :--- | :--- | :--- | :--- |
| 🟢 `●` verde | LIVE | sí | — |
| 🟡 `●` amarillo | REAPPLY | sí (Apply confirma primero) | — |
| 🔴 `●` rojo | NEW_GAME | no | sin motivo inline desde #767 |
| `●` gris | fijado por escenario | no | sin color propio ni entrada en la leyenda (#767) |

- `fieldState()` respeta el orden de S3a: **el candado de escenario gana** sobre el estado del
  catálogo (una clave LIVE puede quedar bloqueada).
- Desde #767 los campos **no repiten el motivo** (`(requires a new game)` fuera): el estado lo
  llevan el glifo/color y la leyenda, que vive **en el pie**, bajo los botones
  (`legendStates()`: LIVE, REAPPLY y NEW_GAME; la entrada blanca de escenario se quitó).
- Las filas mantienen las convenciones del formulario: `● Name:` + columna de página (el prefijo
  de 2 celdas se suma a `valueColumn()`), commit con Enter/Tab y tooltip de rango en los campos
  editables; los deshabilitados usan el tema `INSENSITIVE` (gris sobre negro).

### Pestaña Terrain

Desde #767 la pestaña Terrain muestra el **bloque compartido de Nueva Partida**
(`NewGameDialog.TerrainControls`): seed, sliders de Water/Mountain, la **barra de tres colores** y
el **preview** debajo, con los parámetros actuales del mundo y todo en **read-only**
(`setEnabled(false)`: mismo aspecto, fuera del ciclo de foco). Los campos de terreno del catálogo
(seed/water/mountain) ya no se listan como filas; su estado NEW_GAME queda implícito en el bloque.

En 3D (#778) la página Terrain reproduce el mismo bloque con widgets scene2d
(`Gdx3DHud.fillSettingsTerrain`): `TextField` y sliders deshabilitados (el estilo *disabled* del skin
los pinta grises), la semilla con `TERRAIN_SEED_WIDTH` (140 px), la barra de `TERRAIN_BAR_WIDTH`
repartida con `stackedBarWidths` (misma resta mayor que el 2D) y el mapa de `TerrainPreview.sample`
pintado a 2x en un `Pixmap`/`Texture` que el diálogo libera al cerrar. Nada entra en el ciclo de
foco, así que el diálogo fija el foco al footer.

## Acciones y confirmaciones

- **Apply**: envía solo los campos editables. Si algún REAPPLY cambió respecto al baseline
  (`changedReapplyKeys()`), confirma antes ("These settings need a reapply: …"). Los rechazos de
  `applyLiveSettings` se listan en un diálogo (`key — motivo`) y la ventana **no se cierra**; sin
  rechazos, el baseline pasa a ser lo aplicado y el pie muestra "Settings applied.".
- **Restore**: reescribe todos los campos editables con el baseline (valores efectivos actuales o
  los últimos aplicados).
- **New game** (antes `New game with these values…`, #767): devuelve `Action.StartGame` con
  **todos** los campos (los deshabilitados incluidos) para pre-rellenar el formulario de Nueva
  Partida; misma confirmación de descarte y mismo `Alt+N`.
- **Save as defaults**: confirmación ("Write these values into letrain.cfg?") y escritura; el
  resultado (o el error) se muestra en la línea de feedback.
- **Close**: cierra (Esc también).

## Layout

- **Ventana centrada, acotada y con marco** (#742 polish): a diferencia de la primera versión de
  Nueva Partida, Settings 2D se dibuja como una caja sobre la partida en curso, igual que el
  diálogo 3D. La ventana usa `CENTERED` + `FIXED_SIZE` y **decoración de una línea** de Lanterna
  (`DefaultWindowDecorationRenderer`), con el **título** `SettingsText.TITLE` centrado en el borde
  superior (`CENTER_TITLE` en el tema oscuro). El tamaño fijo evita que la caja cambie al pasar de
  Terrain a Economy.
- **Fondo con la partida**: `DialogWindow.gui(screen)` construye el `MultiWindowTextGUI` con un
  `FrozenBackground` que repinta la **instantánea del front buffer** (`Screen.getFrontCharacter`)
  capturada antes de abrir el diálogo. Lanterna rellena con espacios antes de dibujar el fondo, así
  que un fondo transparente no conservaría el mapa; la instantánea estática sí (el juego está
  parado mientras el diálogo es modal). `TerminalView.showSettingsDialog` repinta una vez antes de
  abrir para que el File menu no deje su último frame en la instantánea.
- **Geometría** (`DialogWindow.dialogSize(screen)`, función pura): el tamaño devuelto es el **área
  de contenido**, fuera de la decoración (1 celda por lado). El ancho es `clamp(screen.columns / 2,
  MIN_COLUMNS=70, MAX_COLUMNS=100)` recortado a `screen.columns - 2*DECORACIÓN - 2*MARGIN_COLUMNS`
  (2 columnas); el alto es `clamp(available rows, MIN_ROWS=20, MAX_ROWS=30)` recortado al espacio
  disponible, con `MARGIN_ROWS=1`. A **80×24** sale un contenido de **70×20** y un **decorado de
  72×22** (márgenes 4 columnas / 1 fila); a 120×30 → 70×26; 160×30 → 80×26; 200×60 → 100×30;
  60×20 → 54×16. En terminales más pequeños que el mínimo se recorta al espacio disponible y el pie
  vuelve a dos filas.
- La columna Terrain recibe `terrainSize(dialog, footerRows)`: el alto del contenido menos las
  filas extra del pie respecto a Nueva Partida (leyenda y feedback), no el tamaño del terminal. A
  80×24 son **70×18**, de donde sale un preview **10×10** (5 filas de texto); el preview se calcula
  con ese tamaño para que ninguna fila quede oculta dentro del marco.
- Fila de pestañas + línea en blanco + una columna de margen, como Nueva Partida (#757). En el pie,
  de arriba a abajo: filas de botones, **leyenda de colores** y línea de feedback (#767).
- Footer adaptativo (`footerRows(columns)`, función pura): con la etiqueta corta `New game`, los
  cinco botones caben en **una fila** a 70 y a 120 (`Apply Restore New game Save as defaults Close`,
  65 celdas); en terminales más estrechas se reparten en dos filas conservando el orden (a 50:
  34 + 30).
- Atajos: **Alt+A/R/N/S/C** para las acciones, **Alt+T/G/E/B/U** para las pestañas y
  **Ctrl+Tab**/**Ctrl+Shift+Tab** para ciclarlas; **Tab** recorre solo los campos de la página
  activa más los botones del pie (las pestañas no son focusables, #757).

## Paridad 3D (S4b, #778)

`Gdx3DHud.showSettingsDialog(currentSettings, scenarioKeys, port, onNewGame)` es el espejo 3D
scene2d del diálogo 2D, con las mismas palabras (`SettingsText`) y la misma semántica:

- **Selector de categorías**: fila de botones (`SettingCategory.displayName()`) que intercambia la
  página visible (una categoría a la vez: Economía tiene ~15 campos). El contenido va en un
  `ScrollPane` para que ninguna categoría desborde.
- **Filas**: una por `SettingDefinition` de la categoría, en orden de catálogo, con el glifo de
  estado (mismo criterio que 2D, vía `SettingsCatalog.fieldState`), la etiqueta con unidad
  (`settingsFieldLabel`) y un `TextField` **editable solo en LIVE/REAPPLY**; los BOOLEAN montan un
  `CheckBox` (mismo estado de edición, fuera del ciclo de Tab cuando son de solo lectura); en
  NEW_GAME y SCENARIO el valor es una etiqueta de solo lectura (nunca editable). Todas las páginas
  llevan un gutter izquierdo de `SETTINGS_LEFT_GUTTER` (una columna de glifo, paridad con el margen
  de una celda del 2D).
- **Leyenda** abajo (`settingsLegendMarkup`, tres entradas, sin escenario) y **línea de feedback**
  con `SettingsText.APPLIED/RESTORED/SAVED_DEFAULTS/SAVE_DEFAULTS_FAILED`.
- **Acciones** (`Apply`/`Restore`/`New game`/`Save as defaults`/`Close`): `Apply` recoge los campos
  editables y confirma los REAPPLY cambiados con `showConfirmDialog` (`REAPPLY_TITLE` +
  `reapplyMessage`); los rechazos se listan en el panel de mensajes del HUD (`REJECTED_TITLE` +
  `rejectedMessage`) sin cerrar. `Restore` reescribe el baseline. `New game` cierra y llama a
  `onNewGame(prefill)`, que mantiene la confirmación de descarte de S4a. `Save as defaults` confirma
  (`SAVE_DEFAULTS_TITLE/CONFIRM`) y escribe mediante `ConfigFileWriter.writeDefaultsOrMessage`.
  `Close`/Esc cierra.
- **Teclado**: Tab/↑/↓ recorren los campos de la página activa (un `TextField` o un `CheckBox`
  editable) y los cinco botones (←/→ se reservan al caret dentro de un `TextField`), Enter/Espacio
  activan un botón o conmutan un `CheckBox` enfocado y Esc cierra. El layout es code-built sobre el
  skin existente, al que S4b/#780 añade estilos (`Slider` con track y knob redondo, variantes
  *disabled* y `CheckBoxStyle`) y un `TextFieldFilter` que descarta los caracteres de control.

## Tests

- `ui-terminal`: `SettingsDialogTest` (estados, prioridad del candado y leyenda de tres entradas,
  ajuste de los botones, confirmación solo con REAPPLY cambiado, columna con marca, recogida de
  valores, mensaje de rechazos y geometría del contenido/decorado), `DialogWindowTest` (instantánea
  del fondo y título centrado del marco), `NewGameDialogTest` (bloque de terreno compartido
  read-only, #767), `TerminalPresenterSettingsTest` (valores efectivos + semilla, port S3a, atajo de
  nueva partida con confirmación y prefill, escritura/error de `letrain.cfg`),
  `TerminalViewFileMenuStateTest` (Settings deshabilitado sin partida y habilitado en partida).
- `ui-graphic`: `Gdx3DHudSettingsTest` (categorías, estados/glyph colours, etiqueta con unidad,
  leyenda y recogida de valores), `GraphicPresenterSettingsTest` (sin economía no abre, valores
  efectivos + semilla, claves de escenario, delegación del port, escritura y confirmación al
  reentrar en New game), `Gdx3DHudFileMenuTest` (Settings habilitado en partida).
- `core`: `FileMenuTextTest` (estados del menú 2D/3D), `SettingsFieldStateTest`
  (`fieldState`/`overDefaults`/`changedReapplyKeys`), pruebas de S3a para `applyLiveSettings`,
  `applyScenarioConfiguration`, `getScenarioSettingKeys` y `ConfigFileWriter`
  (`writeDefaultsOrMessage`).

## Pendiente (otras rebanadas)

- **S4c**: pestañas del catálogo, preview y barra del formulario de Nueva Partida 3D.
- **#417**: flujo completo Guardar/Descartar/Cancelar.
