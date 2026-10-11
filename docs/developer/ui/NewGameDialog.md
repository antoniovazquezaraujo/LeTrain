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
fila de pestañas (`< 1 Terrain > < 2 Game > …`) corona la ventana y el pie (1 fila:
Start/Cancel + atajos) la cierra; el área central es la sección activa. Los controles de terreno
(semilla, sliders, barra, estado y preview) viven **solo en la pestaña Terrain** (#749): antes se
quedaban fijos sobre todas las secciones.

`NewGameDialog.previewPlan(columns, rows)` es una función pura. La rejilla muestreada es
**cuadrada** (`columns == sampleRows`): la ventana de calibración es cuadrada y cada celda
muestreada abarca el mismo cuadrado de mundo, así que los medios bloques (dos filas de muestreo
por fila de terminal) conservan las celdas 1:1 del mundo. Una rejilla ancha estiraba el mundo en
horizontal (#749).

| Terminal | Muestreo | Filas de texto |
| :--- | :--- | :--- |
| < 80×24 | sin preview | — |
| 80×24 | 34 × 34 | 17 (2 muestras por fila con `▀`) |
| ≥ 120×30 | 46 × 46 | 23 |

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
- Sin preview, los sliders y la línea **objetivo vs medido** siguen visibles (concepto §7); la
  línea usa porcentajes compactos (`25%/10%/65%`) para caber también en 70 columnas.

## Secciones

Las pestañas **Terrain / Game / Economy / Construction / UI** se generan iterando
`SettingsCatalog.definitions()`:

- `SettingCategory.WORLD` se muestra como **Game** (`displayName()`).
- **Una sola columna con scroll**: cada campo es una fila `etiqueta (rango)` + valor + unidad.
  `fieldLabel()` deja el rango sin unidad (`Water (0..95)`, `Normal track delay (0+)`) y
  `fieldUnit()` da la unidad completa (`%`, `°`, `cells`, `ticks`). La columna (`ScrollColumn`) se
  desplaza siguiendo el foco (Tab/flechas) y marca la posición con una barra fina en el borde
  derecho cuando hay desbordamiento.
- Campos numéricos → `TextBox`; booleanos → `CheckBox`.
- **Start** vuelca todos los widgets al `NewGameForm` y llama a `toSettings()`; un valor inválido
  abre el diálogo de error del catálogo y no cierra la ventana.

## Atajos (Alt)

El diálogo no tenía teclas de acceso funcionales (#749). Ahora:

- **Alt+1..5**: cambia de pestaña (el número va resaltado en cada pestaña).
- **Alt+N**: sortea una semilla nueva; **Alt+S**: Start; **Alt+C**: Cancel (además de Esc).
- El pie recuerda los atajos y las letras mnemónicas de los botones van resaltadas con
  `TerminalView.mnemonicRenderer`, el mismo estilo que el pie del editor.

## Tests

- `core`: `NewGameFormTest`, `TerrainPreviewTest`, `ModelNewGameTest`, `NewGameTextTest`,
  `PlayerCommandExecutorNewGameTest`, `FileMenuTextTest` (estados 2D portada/partida y 3D).
- `ui-terminal`: `NewGameDialogTest` (plan de preview cuadrado, scroll, barra apilada, etiquetas,
  colores del terreno y atajos), `TerminalPaletteTest` (incluye el modo del terminal expuesto),
  `TerminalPresenterNewGameTest` (portada → menú → partida, confirmación, cancelar, consola).
- `ui-graphic`: `Gdx3DHudFileMenuTest` fija los motivos de bloqueo 3D («3D parity slice»).

## Pendiente (otras rebanadas)

- **S3**: diálogo Settings en partida con estados 🟢/🟡/🔴 y «empezar nueva partida con estos
  valores».
- **S4**: paridad 3D (mismo formulario y preview gráfico).
- **#417**: flujo completo Guardar/Descartar/Cancelar en lugar de la confirmación simple.
