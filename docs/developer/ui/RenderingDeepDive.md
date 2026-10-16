# Motor de Renderizado (Visitor Pattern)

El motor de renderizado de LeTrain está diseñado para ser agnóstico del motor gráfico subyacente. Se basa en el **Patrón Visitor** para que cada entidad sepa cómo dibujarse sin importar el contexto visual.

## Interfaz `Visitor`
Define el contrato para "visitar" cada tipo de entidad en el juego:
- `visitRailTrack(RailTrack)`
- `visitForkRailTrack(ForkRailTrack)`
- `visitLocomotive(Locomotive)`
- `visitWagon(Wagon)`
- `visitGroundMap(GroundMap)`

## Implementaciones de Renderizado
Existen dos implementaciones principales de este contrato:
1. **`letrain.visitor.gdx3d.Gdx3DRenderer`**: Utiliza LibGDX para renderizar modelos 3D (`ModelInstance`). Cada método `visit` utiliza sub-renderizadores especializados (`TrackRenderer`, `VehicleRenderer`, etc.) que seleccionan los modelos y los posicionan en el espacio 3D.
2. **`letrain.visitor.terminal.RenderVisitor`**: Utiliza Lanterna para dibujar en una terminal 2D con caracteres ASCII/UTF-8. Los métodos `visit` eligen el símbolo (e.g., `#` para vías, `T` para trenes) basándose en la orientación y el estado.

## Cómo Añadir una Nueva Entidad Visual
1. Crear la clase de la entidad (e.g., `SignalLight`).
2. Añadir el método `accept(Visitor)` a la entidad:
    ```java
    @Override
    public void accept(Visitor visitor) {
        visitor.visitSignalLight(this);
    }
    ```
3. Añadir `visitSignalLight(SignalLight)` a la interfaz `Visitor`.
4. Implementar el dibujado en todos los renderizadores disponibles.

## Interpolación visual continua

El renderer 3D (`VehicleRenderer`) usa un sistema de interpolación de dos fases para suavizar el movimiento entre celdas, ya que la simulación física solo actualiza posiciones en ticks discretos (~20 TPS):

- **Phase 1** (`progress < 0.5`): El vehículo se desplaza desde el centro de la celda actual hacia la salida usando una curva de Bézier cuadrática.
- **Phase 2** (`progress >= 0.5`): El vehículo entra en la celda siguiente, moviéndose desde la entrada hacia el centro.

El `progress` se calcula a partir de `turns` (contador de ticks hasta el siguiente movimiento físico) y `animationAlpha` (factor de interpolación entre frames de render).

## Colisiones y `canEnterNext`

Para evitar que los vehículos se dibujen dentro de celdas ocupadas por otros trenes durante la interpolación, `PathGeometry.calculateTwoStagePath()` recibe un parámetro `canEnterNext`:

- Si `canEnterNext == false`: el vehículo se queda en el centro de su celda actual sin desplazarse (Phase 1 con `t = 0.5f`, Phase 2 no se ejecuta).
- Si `canEnterNext == true`: interpolación normal.

El cálculo de `canEnterNext` usa un **lookahead encadenado**: desde la celda del vehículo, sigue la cadena de linkers del mismo tren hacia adelante hasta encontrar una celda libre (OK) u ocupada por otro tren (BLOQUEADO). Esto permite que un vagón al final de un tren sepa que la locomotora al frente está bloqueada, y no intente entrar visualmente en su celda. (Ver [ADR-007](../adr/ADR-007-Collision-Visual-Interpolation.md))

## Bypass cuando el tren está parado

Cuando `speed == 0` o `train.isStalled()`, el renderer no llama a `calculateTwoStagePath()` en absoluto: dibuja el vehículo directamente en el centro exacto de su celda (`getPosition() + 0.5`), igual que el renderer 2D.

## Ciclo día/noche

Ambos renderers leen la hora del `GameClock` del modelo y aplican la paleta compartida; el ratio
`getDayNightRatio()` (0 = pleno día, 1 = noche) sale de `SolarModel` (día del año, hora solar y
latitud mundial). El detalle de tokens y fases está en
[DayNight_Colors.md](../systems/DayNight_Colors.md) y la decisión de diseño en
[ADR-022](../adr/ADR-022-Game-Time.md).

- **3D**: `GraphicPresenter.updateDayNight()` aplica `VisualPalette` en cada tick a la luz ambiental,
  al sol direccional (dirección real desde `SolarModel`), al cielo/`VOID` (repartidos por la línea de
  horizonte con `glScissor`), al tablero y a los materiales de terreno, vía y trenes
  (`Gdx3DResourceContext.applyTerrainPalette`). Los avisos y resaltados conservan su color.
- **2D**: `RenderVisitor` resuelve `TerminalPalette` por **niveles** (20, con histéresis direccional)
  y pinta cada token; las celdas dentro del cono del faro (`Headlight`) mezclan su color nocturno con
  el diurno, incluido el fondo.
- **Faros**: `VehicleRenderer` publica las posiciones renderizadas de las locomotoras con luz y el
  presentador dibuja hasta 4 charcos suaves con falloff cuadrático por
  fragmento (`HeadlightGlows` + `HeadlightGlowRenderer`) en las más cercanas a la cámara, con
  opacidad `VisualPalette.lightsOnFactor(ratio)`. Sustituyen a los `PointLight` originales, que
  sobre el suelo de un quad por celda dibujaban la rejilla como manchas cuadradas (#690); el plano
  del charco se pinta por encima del balasto (y por debajo de vía y tren) para que las cajas por
  celda del balasto no lo recorten. Luces y haz solo se encienden por encima de
  `VisualPalette.LIGHTS_ON_RATIO` (0,1) y con el motor en marcha y la locomotora de cabeza
  (`isEngineOn() && isHeadLocomotive()`); en 2D el túnel oculto también apaga el haz.

## Menú de modos: paridad 2D/3D (#710)

El panel de menú (modos de juego) se construye en ambos clientes a partir de la **misma fuente**:
`ModelReportService.createMenuModel`, expuesta por `model.getMenuModel()`. Entradas, orden,
`enabledIf`, `selectedIf` y `doWhenSelected` son compartidos; cada cliente solo decide cómo
pintarlos.

- **Parser compartido**: `letrain.mvp.MenuText` (core). El primer `&` de `gameModeName` marca el
  siguiente carácter como atajo (`&Rails` → `("", "R", "ails")`, `S&ensors` → `("S", "e", "nsors")`).
  Ambos clientes usan `MenuText.parse` y ninguno implementa su propio parseo del `&`.
- **Texto de ayuda**: `MenuText.selectedHint(description, recording)` compone la línea del modo
  seleccionado (`... | [R]:Record ON/OFF | [X]:Experiment`), idéntica en 2D y 3D. El flag de
  grabación sale del journal de comandos (`isRecording() && isSimulationPaused()`).
- **Colores**: 2D `TerminalView` (blanco normal, `GREEN_BRIGHT` para el atajo, gris exacto
  `#808080` —`MENU_DISABLED_FG_COLOR`, un `TextColor.RGB` independiente del tema— para
  deshabilitado sin atajo verde, fondo `BLUE` para seleccionado; las líneas de info/ayuda mantienen
  `DISABLED_FG_COLOR`) y 3D `Gdx3DHud` (marcas `[WHITE]` y `[GREEN]`; el deshabilitado se pinta
  `[GRAY]`, el `Color.GRAY` de libGDX = `#808080`, sin atajo verde; fondo `checked` azul ANSI). Los
  nombres concretos de color son la adaptación de cada tecnología, pero el criterio es el mismo.
- **Niveles de ayuda** (`helpLevel`): 2 = completo, 1 = compacto, 0 = oculto. Tab cicla 2→1→0→2 en
  ambos presentadores y el nivel vive en el `Model`, no en la vista. La descripción del modo
  seleccionado solo se muestra en nivel completo (`HudHelp.showSelectedHint`), como la barra de
  ayuda 2D; en 3D la excepción es el modo `COMMAND`, cuya línea de consola se pinta en esa misma
  etiqueta.

### Bloque de menú 3D (fases 2-3)

Para que las capturas 3D y 2D coincidan, el strip inferior del HUD 3D (`bottomContainer`) se
organiza en **dos columnas** (`Gdx3DHud.STRIP_COLUMNS`):

- **Izquierda: bloque de menú** (ocupa el espacio restante) con **cuatro filas** en el mismo orden
  que el `menuBox` 2D (`Gdx3DHud.MENU_BLOCK_ROWS`). La tabla se ancla a la izquierda
  (`Gdx3DHud.MENU_BLOCK_ALIGN`): sin esa alineación Scene2D centra el contenido cuando es más
  estrecho que su celda y aparecía un hueco a la izquierda en TRAINS/PROGRAM/consola (hint corto).
  1. **Menú**: los 12 modos en una sola línea, alineados a la izquierda. Son `TextButton` del
     estilo `menu-button`, **aplanados** (sin fondo ni padding) para leerse como la tira de texto
     2D; el bloque azul del seleccionado y la clickabilidad se conservan (hover/press muy
     sutiles). La separación entre entradas es un `padRight` fuera del botón, así el bloque azul
     cubre solo el texto, como en 2D.
  2. **Estado del tren** (`Gdx3DHud.trainStatusText`): `Train: N | Speed: <barra> X->Y | Wagons: N`,
     el mismo formato que la línea 2D, con la barra de 10 celdas (verde = velocidad actual, rojo =
     objetivo, gris = vacío). Es **el único indicador de notch** que queda: la palanca gráfica
     antigua se eliminó. Cuando el tren seleccionado ha medido alguna parada de su itinerario,
     ambas líneas 2D/3D añaden `| Punct: +2` con el desfase actual firmado
     (`Punctuality.compact()`, la misma fuente que `info train N`); sin horas no se muestra nada
     (fase 2c del [ADR-022](../adr/ADR-022-Game-Time.md)).
  3. **Hint del modo** (`MenuText.selectedHint`), en gris `#808080` como la barra de ayuda 2D. Si
     no cabe en la columna, se recorta con elipsis (no invade la columna derecha).
  4. **Teclas** (`Gdx3DHud.keysText`), misma caja/puntuación y caja normal que la fila 2D, solo con
     los bindings que existen de verdad en `Gdx3DInputHandler`: `Alt+▲▼`/rueda (zoom), `Alt+◀▶`
     (rotar), `z/Z` (cámara, salvo en TRAINS), letras de modo, `Tab` (panel) y `Esc` (menú). No
     hay binding 3D para `PgUp/PgDn`, así que esa tecla 2D se omite. Conserva el tamaño `tiny`
     original de la línea de teclas 3D para que la fila completa quepa.
- **Derecha: líneas de estado al borde de pantalla**, ambas alineadas a la derecha
  (`Gdx3DHud.STATUS_LINE_ALIGN`), pos/step arriba y economía justo debajo:
  - **Posición y step** (`Gdx3DHud.systemInfoText`): `|Pos:x,y|Step:a/b|` (más `Saved:HH:MM|`
    cuando hay guardado), replicando el `InfoVisitor` 2D **sin** la parte `Page:` porque el 3D no
    tiene paginación.
  - **Finanzas compactas** (`Gdx3DHud.financeText`): `|In:...|Out:...|$:...|`, mismo texto y
    formato (`Locale.US`, dos decimales) que la barra de info 2D. El bloque grande de
    balance/ingresos/gastos del HUD 3D se elimina.

**Glifos**: FreeType solo rasteriza los caracteres declarados en
`FreeTypeFontParameter.characters`; `FontManager.EXTRA_CHARS` declara los no ASCII del HUD
(`■ □ … á é í ó ú`, flechas `←↑→↓⏴⏵⏶⏷` y triángulos `▲▶▼◀`). Sin esa lista, la barra de notch, la
elipsis y los acentos salían como `?`.

Las filas 3 y 4 se liberan de su celda al ocultarse (`setRowVisible`): en Scene2D un actor
invisible sigue reservando su altura (#652). El texto de los hints del modelo se normaliza en
`ModelReportService` (`[key]:Action`, caja normal) para que ambos clientes muestren exactamente lo
mismo.

Diferencias intencionales (tecnología, no información):

- 2D pinta una fila de texto; 3D usa botones Scene2D aplanados (clickables) para esa misma fila.
- La fila de teclas lista los bindings propios de cada cliente (en 2D `PgUp/Dn`/cámara de
  terminal; en 3D Alt/rueda/`z`), con idéntica caja y puntuación.
- El 3D ya no antepone `Selected: <carga>` a la descripción de `TRAINS`: era información exclusiva
  del HUD 3D y rompía la paridad del texto. Si se quiere recuperar, debe añadirse al modelo
  compartido.
- La línea de consola y los hints 3D no hacen wrap: se recortan (el hint con elipsis) en lugar de
  crecer en varias líneas.

## Portada y menú File: paridad 2D/3D (#743, arte 2D #771, captura 3D #775, boot 3D #777)

La portada y el menú File también nacen de fuentes compartidas en `core`, de modo que las
palabras no pueden divergir entre clientes:

- **Portada 2D**: usa el **arte braille** (issue #771) **siempre**; el tamaño del terminal no
  decide nada: si el arte es mayor se centra y se recorta en simétrico (el usuario baja el zoom si
  quiere verla entera). El **estilo** sí depende de la profundidad de color (`CoverStyle`, decidido
  por `CoverStyle.forPalette`, expuesto en tests por `TerminalView.coverStyle`):
  `TRUECOLOR`/`INDEXED_256` pintan el **panel completo** (glifo + fondo + primer plano); los
  terminales **dumb** (`TerminalPalette.Depth.ANSI_16`, donde caen `TERM=dumb`, vacío o ausente)
  pintan **solo puntos**: los glifos braille/ASCII visibles en color sobre el fondo por defecto del
  terminal, y las celdas en blanco (`0020`/`2800`) no se pintan (así la "placa" del rótulo deja
  huecos limpios). Desaparece la portada ASCII antigua. Los datos viven en `core` (`CoverArt`,
  recurso `/letrain/cover/cover_u44.data`) y el pintado Lanterna + la traducción de profundidad, en
  `ui-terminal` (`letrain.mvp.impl.terminal.CoverArtPainter`), así que `core` sigue sin conocer
  Lanterna. **No hay texto de "pulsa una tecla" en 2D** (decisión del dueño): cualquier tecla
  continúa igual que antes.
- **Portada 3D**: `Gdx3DHud.showCover()` pinta la captura del tren de letras
  (`letrain/cover/cover_3d.png`, issue #775) a pantalla completa en modo *cover* —fondo negro
  debajo, escala `max(anchoP/imgAncho, altoP/imgAlto)`, centrada, el sobrante del eje mayor se sale
  de pantalla, sin deformar y sin barras (`coverScale`/`coverOffset`, estáticos puros testeables sin
  GL)—. Tampoco muestra texto (decisión del dueño: la imagen ya rotula LeTrain), y al quedar sin
  uso en ningún cliente se eliminaron las constantes `CoverText` (`TITLE`/`PROMPT`). Si falta el
  recurso, la portada degrada al fondo negro; la textura se libera en `dispose()`.
- **Recorte centrado**: `CoverArtPainter.paint` sitúa el arte con `centredOffset` (división entera
  hacia abajo, luego el offset puede ser negativo) y **salta** las celdas que caen fuera de la
  pantalla, sin pasar coordenadas negativas a Lanterna (aplica a ambos estilos).
- **Formato del asset**: 44 líneas de celdas de 16 caracteres fijos, `cp4hex` (codepoint: braille
  U+2800–U+28FF, ASCII del rótulo/subtítulo o `0020`) `+ fg6hex|------ + bg6hex|------`. `------`
  significa "sin color" y el render aplica el fallback del ANSI de referencia: sin `fg` se usa el
  `bg` (o negro) y sin `bg` se usa el `fg` (o negro); las celdas sin fondo del asset son todas el
  bloque braille sólido U+28FF, así que `fg == bg` sigue pintando una celda llena. Color por celda
  traducido con `TerminalPalette.colorOf(int)` (TRUECOLOR / INDEXED_256 / ANSI_16).
- **Procedencia**: el asset se generó en el laboratorio de color
  (`letrain-lab/demo/alco-color/`, no versionado) a partir del recorte `alco-1.png` del dueño
  (imagen RENFE 318-008-0); lo versionado es solo el `.data` copiado a
  `core/src/main/resources/letrain/cover/cover_u44.data`.
- **Estado de portada**: vive en el presentador (`coverActive`), no en la vista. Cualquier tecla
  continúa; la tecla que despide la portada se consume y **nunca** llega al juego como acción
  (`TerminalPresenter` drena la ráfaga de teclas leídas; `Gdx3DInputHandler` traga el `keyDown` y
  su `keyTyped`). `setCoverEnabled(false)` la salta en contextos headless/lanzamiento directo de
  escenario (deja `gameStarted = true`, así no hay boot); `letrain-check` ni siquiera arranca un
  presentador.
- **Arranque interactivo 3D** (#777, D8): el 3D sigue el mismo flujo que el 2D. `create()` marca
  `gameStarted = false` al mostrar la portada si el cover está habilitado; al despedirla con
  cualquier tecla, `dismissCover()` abre el **menú File** en vez de entregar el mundo (equivale al
  `startupMenuStep()` 2D). `hasActiveGame()` refleja `gameStarted`, así que el menú de boot
  deshabilita `Save`. El `Esc` del menú durante el boot devuelve a la portada
  (`onFileMenuDismissed`); en partida no hace nada. `New`/`Load` arrancan partida: `New` abre el
  diálogo de Nueva Partida (`Gdx3DHud.showNewGameDialog`) y `startNewGame` reutiliza la maquinaria
  de `applyLoadedModel` (cámara/HUD/input/journal) marcando `gameStarted = true` y ocultando la
  portada; cancelar el diálogo `New` o el selector de `Load` en el boot vuelve a la portada (2D
  parity). El separador de callbacks del menú distingue `close` (solo oculta; una acción actuó) de
  `dismiss` (oculta + avisa al boot).

- **Menú File** (`FileMenuText`): `items()` define estructura, orden, palabras y estado
  (`enabled` + `disabledReason`) de `New`/`Load`/`Save`/`Settings`/`Quit`, y `displayLabel(item)`
  añade el motivo entre paréntesis mientras la entrada está bloqueada. `MenuState.terminal(hasGame)`
  y `MenuState.graphic(hasGame)` comparten el patrón contextual: `New` habilitado, `Save` y
  `Settings` según haya partida (`no game in progress`) y `Load`/`Quit` siempre. Desde S4b (#778)
  ambos clientes habilitan `Settings` en partida y lo deshabilitan en portada con el mismo motivo;
  ya no hay diferencia entre `terminal(hasGame)` y `graphic(hasGame)`. `Load`/`Save` abren sus
  diálogos y `Quit` sustituye al antiguo cierre directo con `Esc` (decisión D11). `Esc` abre el
  menú (`view.showFileMenu()` en ambos presentadores) y `Esc` de nuevo lo cierra.
- **Diálogo Nueva Partida 3D** (#777, V1): `Gdx3DHud.showNewGameDialog(form, onStart, onCancel)`
  reutiliza `NewGameForm` (semilla + sliders de agua/montaña, con la regla cruzada del catálogo) y
  valida con `form.toSettings()`, mostrando el mensaje del catálogo en línea si falla. El skin
  code-built no tenía `Slider`, así que añade un `Slider.SliderStyle` "default-horizontal" (ver
  *Estilo de controles* más abajo). El campo de semilla lleva un `TextFieldFilter` que descarta los
  caracteres de control (#780, ver *Caracteres de control*). Desde el feedback S4b (#780) el bloque
  de terreno es **vivo**: bajo los sliders van la barra tricolor del 100 % y el preview cuadrado
  (`TerrainPreviewView`, 88x88 a 2x) y ambos se recalculan al mover cualquier slider y al editar la
  semilla cuando parsea a entero (se guarda la última semilla válida, como el 2D); el botón "new"
  desapareció, así que la semilla se sortea al abrir y a mano después. El foco se pinta en vivo
  (`paintNewGameFocus`): los sliders cambian al estilo "focused-horizontal" (knob cian) y Start/Cancel
  al estilo "focused" (fondo `SELECTED_BG`); las flechas no roban el caret a los `TextField`. Las
  pestañas del catálogo llegan en S4c.
- **Diálogo Settings 3D** (#778, `Gdx3DHud.showSettingsDialog`): espejo del 2D con las mismas
  palabras y semántica. Un selector de categorías (una página a la vez) y, por cada
  `SettingDefinition`, una fila con el glifo de estado —● verde LIVE, ● amarillo REAPPLY, ● rojo
  NEW_GAME, ● gris SCENARIO—, la etiqueta con unidad y el widget editable solo en LIVE/REAPPLY
  (etiqueta de solo lectura en el resto, nunca editable el scenario-locked). Los ajustes BOOLEAN
  montan un `CheckBox` scene2d (`CheckBoxStyle` "default" con cuadros pintados a mano off/on y
  variantes disabled en gris), con el valor de partida de `form` y un `ChangeListener` que reescribe
  `form` (`"true"`/`"false"`), de modo que `Apply` lo recoge por `editableValues` sin cambios extra;
  no editable ⇒ `setDisabled(true)` y fuera del ciclo de Tab, como el 2D. Todas las páginas llevan
  un gutter izquierdo de `SETTINGS_LEFT_GUTTER` (24 px, una columna de glifo) que reproduce el
  margen de una celda del 2D (`NewGameDialog.LEFT_MARGIN_COLUMNS`). Debajo, la leyenda
  compartida (`SettingsText.LEGEND_*`, sin entrada de escenario, #767) y la línea de feedback
  (`SettingsText.APPLIED/RESTORED/SAVED_DEFAULTS/SAVE_DEFAULTS_FAILED`). Los cinco botones
  (`Apply`/`Restore`/`New game`/`Save as defaults`/`Close`) replican el 2D: `Apply` confirma los
  REAPPLY cambiados (`SettingsCatalog.changedReapplyKeys`) y lista los rechazos en el panel de
  mensajes sin cerrar; `New game` cierra y llama a `onNewGame(prefill)` (con la confirmación simple
  de S4a); `Save as defaults` confirma y escribe `letrain.cfg` mediante
  `ConfigFileWriter.writeDefaultsOrMessage`. El mapeo de estados, el baseline y la detección de
  REAPPLY viven en core (`SettingsCatalog.fieldState/overDefaults/changedReapplyKeys`,
  `SettingFieldState`) y los consumen los dos clientes.
- **Página Terrain del Settings 3D** (#778): en vez de una lista de campos, monta el bloque de
  terreno del New Game 2D en modo lectura: semilla (`NewGameText.SEED_LABEL` + `TextField`
  deshabilitado de `TERRAIN_SEED_WIDTH`, 140 px), sliders de agua/montaña deshabilitados (el estilo
  *disabled* del skin los pinta grises, sin el antiguo tinte `DISABLED_TINT`) con su `"N %"`, y la
  barra tricolor del 100 % de `TERRAIN_BAR_WIDTH` (280 px). El reparto de segmentos usa
  `Gdx3DHud.stackedBarWidths`, copia del algoritmo de restos mayores del 2D, y los colores son los
  tonos diurnos del mundo 3D (`terrainDayColor`: `VisualPalette.color(TERRAIN_WATER/FIELDS/MOUNTAIN,
  0.0)` con **alfa forzado a 1**, ver *Guardián del alfa*). Bajo la barra, un hueco y el mapa:
  `TerrainPreview.sample(seed, water, mountain, 88, 88)` pintado en un `Pixmap` a 2x (176x176 px)
  con filtro `Nearest`. El bloque cabe sin scroll en la zona de campos (660x340) y **nada entra en
  el ciclo de Tab** (#767): los controles están deshabilitados y `categoryFields` queda vacío. El
  `close` libera el preview (`disposeTerrainPreviews`), que se recrea al abrir; el mismo `close` corre
  al elegir New game. Desde el feedback S4b (#780) el preview y la barra salen del componente
  compartido `TerrainPreviewView`/`terrainBarTable`, que el diálogo de Nueva Partida reutiliza en
  modo vivo.
- **Estilo de controles del HUD 3D** (#780): el skin code-built añade al `Slider.SliderStyle`
  "default-horizontal" un track/base con `minHeight` explícito (`sliderTrack`) y un knob circular de
  24x24 (`sliderKnob`, `Pixmap.fillCircle` en el acento) con mínimos cuadrados para no deformarse;
  sin ellos el píxel "white" 1x1 colapsaba el slider a "una aguja". El `CheckBoxStyle` "default"
  usa cuadros 26x26 dibujados a mano (cuadrado oscuro con borde claro; el "on" añade el check en dos
  trazos de acento). Ambos llevan variantes *disabled* en gris, de modo que los controles de solo
  lectura se ven grises de verdad. Desde el feedback S4b (#780) el skin añade también la variante
  "focused-horizontal" del slider (mismo track, knob cian) y el estilo "focused" del `TextButton`
  (fondo `SELECTED_BG` con `over` más claro y texto blanco) que usan todos los diálogos 3D.
- **Caracteres de control en los campos 3D** (#780): el backend LWJGL3 sintetiza `keyTyped('\t')` al
  pulsar Tab y `BitmapFontData.hasGlyph` devuelve `true` para cualquier carácter cuando la fuente
  FreeType tiene `missingGlyph`, así que un `TextField` normal insertaba un `\t` que se dibujaba como
  `?`. Todos los `TextField` de diálogo (semilla del New Game, semilla y campos del Settings) llevan
  `setTextFieldFilter((f, c) -> c >= 32)`, que solo descarta los caracteres de control "add" sin
  afectar a Backspace/Delete/Enter. Los `TextArea`/editor quedan fuera: ahí el tab sí tiene semántica.
- **Guardián del alfa en `terrainDayColor`** (#780): `Color.rgb888ToColor` no fija el alfa del
  `Color` destino (el constructor deja `a=0`), así que la barra y el pixmap del preview salían
  transparentes. `terrainDayColor` fuerza `color.a = 1f` tras convertir; el test
  `Gdx3DHudSettingsTest.should_ReturnOpaqueDayTones` fija `a == 1f` y los RGB diurnos exactos
  (water `0x3366CC`, fields `0x66994C`, mountain `0x80664D`).
- **Glifos y teclado del Settings 3D** (#778): el glifo ● (`SETTINGS_GLYPH`) llega a las filas y a
  la leyenda gracias a que está declarado en `FontManager.EXTRA_CHARS`; FreeType solo rasteriza los
  caracteres listados y cualquier símbolo ausente se dibuja como `?`. El test guardián
  `FontManagerTest.testExtraCharsCoverHudGlyphs` falla si el glifo (o cualquier otro símbolo del
  HUD) desaparece de la lista, con el mensaje "or it renders as '?'". En cuanto al teclado, el
  diálogo fija **siempre** el foco al abrir y al cambiar de página (`categoryFields.get(0)` o, si la
  página no tiene campos —Terrain—, el primer botón del footer): con `keyboardFocus == null`,
  `Stage.keyDown` envía el evento al root y el listener del diálogo no lo oiría, así que Tab/Enter/Esc
  quedarían muertos. Los botones del footer y los checkboxes no tienen caret propio: el botón
  enfocado cambia al estilo "focused" (fondo `SELECTED_BG`, texto blanco) y el checkbox se tiñe de
  cian (`paintFooterFocus`), refrescado tras navegar, tras `showCategory` y cuando las
  confirmaciones (reapply/save defaults) devuelven el foco. Desde el feedback S4b (#780) el diálogo
  añade los accesos **Alt+T/G/E/B/U** (pestañas) y **Alt+A/R/N/S/C** (acciones) con la letra
  resaltada en verde en el label (`settingsHotkeyLabel`, mismo markup que el `mnemonicRenderer` 2D),
  **Ctrl+Tab**/**Ctrl+Shift+Tab** cicla las categorías (`settingsCycleTab`) sin disparar la
  navegación normal, y `navigateSettingsFocus` hace scroll automático al campo enfocado
  (`revealInScroll`, `ScrollPane.scrollTo` con coordenadas del actor) al navegar y al recuperar el
  foco tras una confirmación. El **menú File** y el **diálogo de confirmación** usan el mismo swap de
  estilo "focused" para su foco (fondo, no color de texto).

- **Editor**: los pies 2D (`TerminalView.editorFooterActions`) y 3D (`Gdx3DHud`) ya no llevan
  `Save`/`Load`: quedan `Export`/`Import`/`Reload from game`/`Apply program`/`Rebuild world`/
  `Close`. Los comandos de consola `save;`/`load;` se mantienen.

## Invariantes de la Vista
- Ninguna clase de renderizado debe modificar el estado del `Model`.
- El acceso a los datos de la entidad durante el renderizado debe ser solo de lectura.
- El ciclo de renderizado es independiente del ciclo de simulación (simulación a 20 TPS, renderizado a 60 FPS si el hardware lo permite).
