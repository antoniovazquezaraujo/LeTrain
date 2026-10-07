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
  seleccionado (`... | [R]: Record ON/OFF | [X]: Experiment`), idéntica en 2D y 3D. El flag de
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
     antigua se eliminó.
  3. **Hint del modo** (`MenuText.selectedHint`), en gris `#808080` como la barra de ayuda 2D. Si
     no cabe en la columna, se recorta con elipsis (no invade la columna derecha).
  4. **Teclas** (`Gdx3DHud.keysText`), misma caja/puntuación y caja normal que la fila 2D, solo con
     los bindings que existen de verdad en `Gdx3DInputHandler`: `Alt+▲▼`/rueda (zoom), `Alt+◀▶`
     (rotar), `z/Z` (cámara, salvo en TRAINS), letras de modo, `Tab` (panel) y `Esc` (salir). No
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
`ModelReportService` (`[key]: Action`, caja normal) para que ambos clientes muestren exactamente lo
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

## Invariantes de la Vista
- Ninguna clase de renderizado debe modificar el estado del `Model`.
- El acceso a los datos de la entidad durante el renderizado debe ser solo de lectura.
- El ciclo de renderizado es independiente del ciclo de simulación (simulación a 20 TPS, renderizado a 60 FPS si el hardware lo permite).
