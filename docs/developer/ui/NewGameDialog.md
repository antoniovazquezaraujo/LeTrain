# New Game (2D) — S2, issue #746

Documentación técnica del diálogo **Nueva Partida** del cliente 2D (terminal Lanterna). Es la
segunda rebanada del arco **New Game + Settings** (#742) y consume el catálogo de ajustes de S1.

## Piezas

| Pieza | Módulo | Responsabilidad |
| :--- | :--- | :--- |
| `letrain.settings.SettingsCatalog` | core | Fuente única de metadatos (S1): clave, sección, tipo, rango, unidad, default y estado. |
| `letrain.settings.NewGameForm` | core | Estado del formulario: precarga, regla del terreno (agua + montaña ≤ 95 %), semilla y validación (`toSettings()`). Sin dependencias de UI. |
| `letrain.ground.TerrainPreview` | core | Muestreo del terreno para el preview: mismo `PerlinNoise`, mismo `scaled()` y mismos umbrales calibrados que `GroundMap`. |
| `letrain.mvp.NewGameText` | core | Textos compartidos (título, Start/Cancel, línea objetivo/medido, confirmación). |
| `letrain.mvp.impl.terminal.NewGameDialog` | ui-terminal | Diálogo Lanterna: cabecera de terreno, pestañas de secciones, preview adaptativo y acciones. |
| `Model.newGame(seed, settings)` | core | Gancho mínimo de core: modelo sembrado + `applyConfig` **antes** de generar terreno; reutiliza la maquinaria de escenario/load. |

## Flujo

```
portada --cualquier tecla--> menú File (sin partida: Save deshabilitado)
     New --confirmación si hay partida--> NewGameDialog.show(defaults) --> Map<String,String>
        --> Model.newGame(seed, settings) --> setModel --> mundo en marcha
     Load --> GameSaveService --> setModel --> mundo en marcha
     Esc  --> vuelve a la portada (no hay mundo al que volver)
```

- `Default` al abrir el diálogo: `EconomyManager.effectiveConfig()` (respeta `letrain.cfg`) con los
  defaults del catálogo; semilla aleatoria nueva y proporciones D2 (25 / 10) si no hay claves
  porcentuales.
- `new game;` (consola/scenario) entra por el mismo `onNewGame()`: es una sentencia del grammar
  (`newGameCommand : NEW GAME ;`) y el ejecutor la delega en el callback del cliente. Sin cliente
  con diálogo responde «New game is not available in this context.»

## Layout y preview adaptativo

El diálogo ocupa la pantalla completa sin decoración (`FULL_SCREEN` + `NO_DECORATIONS`). Una única
fila de pestañas (`< T Terrain > < G Game > …`, letra de atajo resaltada, #755) corona la ventana,
debajo va **una línea en blanco** (`SECTION_TOP_ROWS`, en todas las pestañas, #754) y el pie (1
fila: Start/Cancel) la cierra; el área central es la sección activa. Los controles de terreno
(semilla, sliders, barra y preview) viven **solo en la pestaña Terrain** (#749): antes se quedaban
fijos sobre todas las secciones.

`NewGameDialog.previewPlan(columns, rows)` es una función pura sobre `sectionRows(rows)` (terminal
menos pestañas, línea en blanco y pie). La rejilla muestreada es **cuadrada**
(`columns == sampleRows`): la ventana de calibración es cuadrada y cada celda muestreada abarca el
mismo cuadrado de mundo, así que los medios bloques (dos filas de muestreo por fila de terminal)
conservan las celdas 1:1 del mundo. Una rejilla ancha estiraba el mundo en horizontal (#749). Desde
#754 el preview ocupa la **mitad de ancho y alto** del primer diseño (≈1/4 del área), para no
comerse el diálogo, y desde #755 va **pegado al borde izquierdo** (x=0):

| Terminal | Muestreo | Filas de texto |
| :--- | :--- | :--- |
| < 80×24 | sin preview | — |
| 80×24 | 16 × 16 | 8 (2 muestras por fila con `▀`) |
| ≥ 120×30 | 22 × 22 | 11 |

- El preview dibuja **medios bloques** (`▀`): color de fondo = muestra inferior, color de texto =
  muestra superior; cada columna de terminal es una celda de mundo ancha y cada media fila ~una
  celda alta, de modo que la celda de mundo sale cuadrada.
- El muestreo cubre la **ventana de calibración** (0..`TerrainCalibrator.SAMPLE_SIZE`) escalada a
  la rejilla, de modo que objetivo, medido y preview describen la misma región y no bailan entre
  tamaños de terminal.
- Los colores de día salen de la paleta del juego (los del demo de calibración): mar `#3366CC`,
  campo `#4CA331` y montaña **`#80664D`** (antes blanco, #749). `terrainColor()` resuelve el modo
  del terminal: truecolor exacto, 256 el índice más cercano y ANSI-16 el rojo oscuro con el que el
  mapa 2D degrada su roca (no hay marrón en 16 colores, pero nunca blanco).
- Sin preview (< 80×24) los sliders y la barra siguen visibles; el aviso es
  `No preview at this size (needs 80x24+)`. La línea **Target/Measured** desapareció en #755, así
  que `TERRAIN_CHROME_ROWS` baja a 4 (seed, water, mountain y barra) y los tamaños de preview no
  cambian.
- La **barra apilada** ocupa la fila completa del slider: empieza en el borde izquierdo y acaba
  donde termina el porcentaje (`valueColumn + bar + 7`, 59 celdas a 70 columnas) con
  `stackedBarWidth()`, compartiendo `sliderBarWidth()` con el slider.

## Secciones

Las pestañas **Terrain / Game / Economy / Construction / UI** se generan iterando
`SettingsCatalog.definitions()`:

- `SettingCategory.WORLD` se muestra como **Game** (`displayName()`).
- **Una sola columna con scroll**: cada campo es una fila `Nombre: valor` + unidad. La etiqueta es
  solo el nombre (`fieldLabel()`, sin pistas de rango, #751); `alignedLabel()` pega los dos puntos
  al nombre y rellena con espacios hasta la **columna de la página**: `valueColumn(fieldNames())`
  toma el nombre más ancho de esa pestaña + `: ` (#755). Terrain usa la suya (12: Seed, Water %,
  Mountain %) y cada sección la suya (Game 14, Economy 21, Construction 24, UI 26), así que el
  hueco a la izquierda de los valores se recorta en todas las páginas. `fieldUnit()` da la unidad
  completa (`%`, `°`, `cells`, `ticks`). La columna (`ScrollColumn`) se desplaza siguiendo el foco
  (Tab/flechas) y marca la posición con una barra fina en el borde derecho cuando hay
  desbordamiento.
- Campos numéricos → `ValidatingTextBox`; booleanos → `CheckBox`.
- **Commit con Enter/Tab**: `normalizeCommit()` valida contra el catálogo (un seed solo admite
  enteros: `random` se resuelve al abrir). Un valor inválido **no se aplica**: el texto vuelve al
  último válido, el foco se queda en el campo y `TooltipBox` abre una cajita de tres filas bajo el
  campo con el rango permitido (`allowedRange()`: `Allowed: 0..95`, `Minimum: 0`, `Maximum: 12`,
  `Allowed: whole number` para el seed); al corregir y confirmar se cierra. No valida mientras se
  escribe.
- **Start** vuelca todos los widgets al `NewGameForm` y llama a `toSettings()`; un valor inválido
  (p. ej. sin confirmar con Enter/Tab) abre el diálogo de error del catálogo y no cierra la ventana.

## Atajos (Alt)

El diálogo no tenía teclas de acceso funcionales (#749). Ahora:

- **Alt+T/G/E/C/U**: cambia de pestaña por la letra inicial del nombre (`tabHotkey()`,
  `tabLetter()`), que va resaltada en cada pestaña (#755). Los números de #749 ya no valen.
- **Alt+S**: Start (mnemónico del botón). **Cancel queda solo con Esc**: la C pertenece a la
  pestaña Construction (#755).
- El pie es solo Start/Cancel: **no hay texto de atajos** (se quitó `HOTKEY_HINT` en #755). La
  letra mnemónica de Start va resaltada con `TerminalView.mnemonicRenderer`, el mismo estilo que el
  pie del editor.

## Tests

- `core`: `NewGameFormTest`, `TerrainPreviewTest`, `ModelNewGameTest`, `NewGameTextTest`,
  `PlayerCommandExecutorNewGameTest`, `FileMenuTextTest` (estados 2D portada/partida y 3D).
- `ui-terminal`: `NewGameDialogTest` (plan de preview cuadrado, scroll, barra apilada, etiquetas
  limpias, columna por página, ancho de la barra sobre la fila del slider, commit inválido → valor
  anterior + tooltip, colores del terreno y atajos por letra), `TerminalPaletteTest` (incluye el
  modo del terminal expuesto),
  `TerminalPresenterNewGameTest` (portada → menú → partida, confirmación, cancelar, consola).
- `ui-graphic`: `Gdx3DHudFileMenuTest` fija los motivos de bloqueo 3D («3D parity slice»).

## Pendiente (otras rebanadas)

- **S3**: diálogo Settings en partida con estados 🟢/🟡/🔴 y «empezar nueva partida con estos
  valores».
- **S4**: paridad 3D (mismo formulario y preview gráfico).
- **#417**: flujo completo Guardar/Descartar/Cancelar en lugar de la confirmación simple.
