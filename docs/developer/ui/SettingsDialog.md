# Settings en partida (2D) — S3b, issue #764

Documentación técnica del diálogo **Settings** del cliente 2D (terminal Lanterna), abierto desde
**File → Settings** mientras hay una partida en curso (D9/D10, concept §5.2). Es la rebanada S3b
del arco **New Game + Settings** (#742) y consume las APIs de S3a (aplicación por estado, candado
de escenario, escritura de `letrain.cfg`).

## Piezas

| Pieza | Módulo | Responsabilidad |
| :--- | :--- | :--- |
| `letrain.mvp.impl.terminal.SettingsDialog` | ui-terminal | Diálogo Lanterna: pestañas con estados por campo, acciones y feedback. Reutiliza las piezas del formulario de `NewGameDialog`. |
| `letrain.mvp.SettingsText` | core | Textos compartidos (título, botones, leyenda, confirmaciones, mensajes). |
| `Model.applyLiveSettings(Map)` | core | Aplica LIVE/REAPPLY a la partida y devuelve `SettingsApplyResult(applied, rejections)` (S3a). |
| `Model.getScenarioSettingKeys()` | core | Claves fijadas por el `configuration { }` del escenario (candado ⚪, D10). |
| `ConfigFileWriter.writeDefaults(...)` | core | Guarda los valores del formulario en el `letrain.cfg` del juego (D4); lo crea si no existe. |
| `EconomyManager.effectiveConfig()` | core | Valores efectivos actuales: base de Restore y de la precarga. |
| `GameViewListener.onSettings()` | core | Puerto File → Settings (los clientes sin diálogo mantienen el no-op). |

## Flujo

```
File → Settings (solo con partida; en portada deshabilitado con "no game in progress")
   --> SettingsDialog.show(valores efectivos + claves de escenario + SettingsPort)
         Apply            --> Model.applyLiveSettings (S3a); rechazos con motivo, sin cerrar
         Restore          --> vuelve a los valores efectivos actuales (baseline)
         New game with…   --> Action.StartGame(form) --> onNewGame(prefill) [confirmación + diálogo S2]
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

| Marca | Estado | Editable | Motivo que se añade a la fila |
| :--- | :--- | :--- | :--- |
| 🟢 `●` verde | LIVE | sí | — |
| 🟡 `●` amarillo | REAPPLY | sí (Apply confirma primero) | — |
| 🔴 `●` rojo | NEW_GAME | no | `(requires a new game)` |
| ⚪ `●` blanco | fijado por escenario | no | `(defined by scenario)` |

- `fieldState()` respeta el orden de S3a: **el candado de escenario gana** sobre el estado del
  catálogo (una clave LIVE puede quedar ⚪).
- Leyenda pequeña bajo las pestañas con los cuatro significados.
- Las filas mantienen las convenciones del formulario: `● Name:` + columna de página (el prefijo
  de 2 celdas se suma a `valueColumn()`), commit con Enter/Tab y tooltip de rango en los campos
  editables; los deshabilitados usan el tema `INSENSITIVE` (gris sobre negro).

## Acciones y confirmaciones

- **Apply**: envía solo los campos editables. Si algún REAPPLY cambió respecto al baseline
  (`changedReapplyKeys()`), confirma antes ("These settings need a reapply: …"). Los rechazos de
  `applyLiveSettings` se listan en un diálogo (`key — motivo`) y la ventana **no se cierra**; sin
  rechazos, el baseline pasa a ser lo aplicado y el pie muestra "Settings applied.".
- **Restore**: reescribe todos los campos editables con el baseline (valores efectivos actuales o
  los últimos aplicados).
- **New game with these values…**: devuelve `Action.StartGame` con **todos** los campos (los
  deshabilitados incluidos) para pre-rellenar el formulario de Nueva Partida.
- **Save as defaults**: confirmación ("Write these values into letrain.cfg?") y escritura; el
  resultado (o el error) se muestra en la línea de feedback.
- **Close**: cierra (Esc también).

## Layout

- Fila de pestañas + leyenda + línea en blanco + una columna de margen, como Nueva Partida (#757).
- Footer adaptativo (`footerRows(columns)`, función pura): a **120×30** los cinco botones caben en
  una fila (`Apply Restore New game with these values… Save as defaults Close`, 84 celdas); a
  **70×20** se reparten en dos filas conservando el orden (53 + 30) y el pie suma la línea de
  feedback.
- Atajos: **Alt+A/R/N/S/C** para las acciones, **Alt+T/G/E/B/U** para las pestañas y
  **Ctrl+Tab**/**Ctrl+Shift+Tab** para ciclarlas; **Tab** recorre solo los campos de la página
  activa más los botones del pie (las pestañas no son focusables, #757).

## Tests

- `ui-terminal`: `SettingsDialogTest` (estados y motivos, prioridad del candado, ajuste de los
  botones a 70/120, confirmación solo con REAPPLY cambiado, columna con marca, recogida de valores
  y mensaje de rechazos), `TerminalPresenterSettingsTest` (valores efectivos + semilla, port S3a,
  atajo de nueva partida con confirmación y prefill, escritura/error de `letrain.cfg`),
  `TerminalViewFileMenuStateTest` (Settings deshabilitado sin partida y habilitado en partida).
- `core`: `FileMenuTextTest` (estados del menú 2D/3D), pruebas de S3a para `applyLiveSettings`,
  `applyScenarioConfiguration`, `getScenarioSettingKeys` y `ConfigFileWriter`.

## Pendiente (otras rebanadas)

- **S4**: paridad 3D del diálogo (el menú 3D sigue bloqueado con `3D parity slice`).
- **#417**: flujo completo Guardar/Descartar/Cancelar.
