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

El diálogo ocupa la pantalla completa sin decoración (`FULL_SCREEN` + `NO_DECORATIONS`). La
cabecera (6 filas: pestañas, semilla, 2 sliders, barra apilada y línea objetivo/medido) y el pie
(1 fila: Start/Cancel) son fijos; el área central es la sección activa.

`NewGameDialog.previewPlan(columns, rows)` es una función pura:

| Terminal | Modo | Muestreo | Filas de texto |
| :--- | :--- | :--- | :--- |
| < 80×24 | sin preview | — | — |
| 80×24 | compacto | 76 × 34 | 17 (2 muestras por fila con `▀`) |
| ≥ 120×30 | grande | (cols−4) × 46 | 23 |

- El preview dibuja **medios bloques** (`▀`): color de fondo = muestra inferior, color de texto =
  muestra superior, así cada fila de terminal lleva dos filas de muestreo.
- El muestreo cubre la **ventana de calibración** (0..`TerrainCalibrator.SAMPLE_SIZE`) escalada a
  la rejilla, de modo que objetivo, medido y preview describen la misma región y no bailan entre
  tamaños de terminal.
- Sin preview, los sliders y la línea **objetivo vs medido** siguen visibles (concepto §7).

## Secciones

Las pestañas **Terrain / Game / Economy / Construction / UI** se generan iterando
`SettingsCatalog.definitions()`:

- `SettingCategory.WORLD` se muestra como **Game** (`displayName()`).
- Campos numéricos → `TextBox`; booleanos → `CheckBox`.
- `fieldLabel()` añade rango y unidad de forma compacta (`(0..95 %)`, `(0+ t)`; `ticks` se abrevia
  a `t`). A 120+ columnas se muestran 3 campos por fila; por debajo, 2.
- **Start** vuelca todos los widgets al `NewGameForm` y llama a `toSettings()`; un valor inválido
  abre el diálogo de error del catálogo y no cierra la ventana.

## Tests

- `core`: `NewGameFormTest`, `TerrainPreviewTest`, `ModelNewGameTest`, `NewGameTextTest`,
  `PlayerCommandExecutorNewGameTest`, `FileMenuTextTest` (estados 2D portada/partida y 3D).
- `ui-terminal`: `NewGameDialogTest` (plan de preview, barra apilada, etiquetas),
  `TerminalPresenterNewGameTest` (portada → menú → partida, confirmación, cancelar, consola).
- `ui-graphic`: `Gdx3DHudFileMenuTest` fija los motivos de bloqueo 3D («3D parity slice»).

## Pendiente (otras rebanadas)

- **S3**: diálogo Settings en partida con estados 🟢/🟡/🔴 y «empezar nueva partida con estos
  valores».
- **S4**: paridad 3D (mismo formulario y preview gráfico).
- **#417**: flujo completo Guardar/Descartar/Cancelar en lugar de la confirmación simple.
