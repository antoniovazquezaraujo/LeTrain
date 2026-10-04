# Mapa de colores día/noche (fase 1 de ADR-022)

Estado: **implementado** — fase 1: **1a, 1b, 1c y 1d completas**; **1e parcial** (faros de
locomotora; farolas/ventanas y niebla fina pendientes).

Relacionado: [[ADR-022-Game-Time]] (fase 1), [[ADR-013]] (modo noche/día), issue #480
(paridad 2D/3D).

## Objetivo

Fijar, antes de tocar código, **qué color cambia cada cosa y dónde vive**: los colores estaban
repartidos en literales por los renderers de los dos clientes y, sin un mapa único, ajustar la noche
era tocar veinte sitios a ciegas. El mapa de tokens sigue vigente; lo implementado se resume en
*Resultado implementado*.

Alcance de la fase 1: **mundo** (terreno, vía, elementos, trenes, ambiente). El HUD/skin queda
fuera (solo se revisará su legibilidad al final).

## Resultado implementado (beta.5)

Lo que hay en `develop` a día de hoy; el detalle por fase está al final de la ficha.

- **Reloj**: `GameClock`/`SimpleGameClock` (`core`, `letrain.time`). Arranca en el **día 1 a las
  08:00** y un día completo dura `DEFAULT_DAY_DURATION_SECONDS = 1440` (24 minutos reales;
  configurable con `time.dayDurationSeconds`). Avanza desde `SimulationController.tick()` (20 TPS) y
  la pausa de edición lo congela; se persiste `elapsedTicks` y `GameTime` se deriva.
- **Modelo solar**: `SolarModel` calcula elevación y azimut del sol a partir del **día del año** y la
  hora solar, con la **latitud mundial** (`world.latitude`, por defecto 40°) y una banda de
  **crepúsculo** de 18°. `getDayNightRatio()` (0 = pleno día, 1 = noche cerrada) e `isNight()`
  (ratio > 0,5) salen de ahí; en latitudes extremas hay noches blancas y día/noche perpetuos sin
  casos especiales.
- **`VisualPalette`** (`core`, `letrain.palette`): fuente única de verdad, con tokens de ambiente
  (`AMBIENT_LIGHT`, `SUN_LIGHT`, `SKY`, `TABLE_BOARD`), terreno (1b), vía y trenes (1c) y
  `EMISSIVE_HEADLIGHT` (1e). Claves día/crepúsculo/noche interpoladas en luz lineal; los colores de
  jugador solo se atenúan (`playerColorFactor`: 1,0 → 0,8 → 0,55).
- **3D** (`ui-graphic`): `GraphicPresenter.updateDayNight()` aplica la paleta en cada tick al
  `Environment` (luz ambiental y sol direccional según `SolarModel`), al cielo/`VOID` repartido por
  el horizonte con `glScissor`, al tablero/rejilla/cajas y a los materiales de terreno, vía y trenes
  (`Gdx3DResourceContext.applyTerrainPalette`). Los avisos y resaltados no se atenúan.
- **2D** (`ui-terminal`): `TerminalPalette` (familia clara) resuelve los tokens con degradación
  **24-bit → 256 → 16 ANSI** según `COLORTERM`/`TERM`; el `RenderVisitor` cuantiza el ratio en
  **20 niveles** (`BANDS = 19`) con histéresis direccional y suelo de contraste, y mezcla el fondo
  con su color diurno dentro del haz del faro. El suelo de día y crepúsculo es **verde de campo**
  (`0x4CA331` / `0x527A38`, ajuste del `VisualPalette.TERRAIN_FIELDS` de 3D), no papel: el
  **blanco queda reservado para la nieve futura** (#692).
- **Faros**: umbral compartido `VisualPalette.LIGHTS_ON_RATIO = 0,1` y rampa `lightsOnFactor`. En 3D
  `HeadlightGlows` + `HeadlightGlowRenderer` dibujan hasta 4 charcos suaves (quad con falloff
  cuadrático por fragmento y alpha blending) delante de la locomotora y
  alineados con su rumbo en las locomotoras más cercanas a la cámara (posición **renderizada**
  interpolada) y `VehicleRenderer` pinta las dos lámparas (emisivas o apagadas); en 2D el
  `RenderVisitor` simula el cono (`Headlight`). Los charcos sustituyen a los `PointLight` originales,
  que sobre el suelo de un quad por celda se veían como manchas **cuadradas** siguiendo la rejilla
  (#690); su plano se pinta por encima del balasto (y por debajo de vía y tren) para que las cajas
  por celda del balasto no lo recorten. En ambos clientes las luces y el haz solo se encienden con el
  motor en marcha (`isEngineOn()`) y en la locomotora de cabeza (`isHeadLocomotive()`); en 3D tampoco
  durante el descarrilamiento (`isDestroying()`).

## Cómo funcionaba antes de implementar (histórico)

> Sección conservada como inventario del punto de partida. Las viñetas de 3D y 2D describen el
> estado previo a 1a/1d; la del reloj sigue siendo válida (el reloj es de la fase 0). Los valores de
> color en vigor salen de `VisualPalette` en 3D y de `TerminalPalette` en 2D.

- **Reloj**: `GameClock.getDayNightRatio()` (0.0 = pleno día, 1.0 = noche cerrada) e `isNight()`
  (ratio > 0.5). El ratio sale de `SolarModel` (elevación del sol con banda de crepúsculo de 18°)
  según el día del año y `world.latitude`; a latitudes altas hay noches blancas (el ratio no
  llega a 1) y día/noche perpetuos en los polos.
- **3D**: materiales con `ColorAttribute.createDiffuse(...)` fijos + `Environment` global
  (`AmbientLight` 0.5 gris y `DirectionalLight` 0.8). No hay cielo, niebla ni color de fondo
  configurable (el *clear* es el negro por defecto). Como todo el mundo es difuso y recibe la luz,
  **oscurecer la luz ambiental ya apaga el mundo entero**; encima harán falta retoques por token.
- **2D**: colores ANSI fijos en `RenderVisitor` (constantes y literales) sobre fondo negro
  (`BG_COLOR`). La paleta ANSI es discreta (16 colores): aquí no se interpola, se elige variante
  por franja con **histéresis** en las fronteras.

## Arquitectura (implementada)

El diseño aprobado y llevado a código:

1. **`VisualPalette`**: única fuente de verdad. Enum de **tokens** (`AMBIENT_LIGHT`,
   `TERRAIN_FIELDS`, `TRACK_RAIL`, …) con tres claves —día, crepúsculo y noche (0xRRGGBB)— y
   `color(token, ratio)` que interpola en luz lineal. Vive en `letrain.palette` (`core`) como
   **datos puros**, sin libgdx ni Lanterna; cada cliente los traduce a su tecnología (ANSI en 2D,
   `Color` en 3D). Si crece con detalle de UI, se moverá a un módulo propio (pendiente).
2. **3D**: el `GraphicPresenter` pide cada token con `palette.color(token, ratio)` y lo aplica al
   `Environment`, a los materiales, al tablero y al cielo; la dirección del sol sale de
   `SolarModel`.
3. **2D**: `TerminalPalette.resolve(band)` con 20 escalones de ratio espaciados por luminosidad
   percibida e histéresis (`band(ratio, currentBand)`); el terminal no interpola. **Familia por
   defecto: clara** (día de campo verde con glifos oscuros; noche: fondo oscuro y glifos claros),
   elegida tras ver la demo. El color se emite en **24-bit cuando el terminal lo
   soporta** (`COLORTERM`, `TERM=*-direct`) y si no se degrada a **256** o a los **16 slots ANSI**.
   La configuración `terminal.palette=auto|light|dark|theme` quedó aplazada (siempre familia clara).
4. Los colores **de jugador** (paleta de locomotoras, carga) no se rediseñan: se atenúan con
   `playerColorFactor` (1,0 → 0,8 → 0,55).

## Inventario (el mapa)

Convención: `n` = `[0–1]` por canal. 2D en colores ANSI de Lanterna.

Nota: las columnas «2D actual» / «3D actual» son el inventario **previo a implementar**; se
conservan como referencia. Las columnas Día/Crepúsculo/Noche son el mapeo acordado que se aplicó, y
los valores vivos están hoy en `VisualPalette` (ambiente y 3D) y `TerminalPalette` (2D).

Actualización (beta.6, #692): en 2D `terrain.fields` ya no es `WHITE`; el campo y el tablero son
verde de día (`0x4CA331`) y crepúsculo (`0x527A38`), y el blanco queda reservado para la nieve
futura. En 3D `TERRAIN_FIELDS` no cambia.

### Terreno y estructura

| Token | 2D actual | 3D actual (fichero:línea) | Día | Crepúsculo | Noche |
|---|---|---|---|---|---|
| `terrain.fields` | `WHITE` (RenderVisitor:37) | `(0.4, 0.6, 0.3)` (Gdx3DResourceContext:282) + pared `(0.4,0.6,0.3)` (GroundRenderer:134) | actual | `(0.32, 0.42, 0.22)` | `(0.12, 0.18, 0.14)` |
| `terrain.water` | `BLUE_BRIGHT` (:38) | `(0.2, 0.4, 0.8)` (:286) | actual | `(0.15, 0.28, 0.55)` | `(0.06, 0.12, 0.30)` |
| `terrain.mountain` | `RED_BRIGHT` (:39) | `(0.5, 0.4, 0.3)` (:290) + pared `(0.5,0.4,0.3)` (GroundRenderer:132) | actual | `(0.38, 0.30, 0.22)` | `(0.16, 0.14, 0.12)` |
| `terrain.ballast` | (no existe; usa vía) | `(0.5, 0.5, 0.5)` (:294) | actual | `(0.36, 0.34, 0.30)` | `(0.14, 0.14, 0.15)` |
| `structure.bridgePillar` | n/a | `(0.5, 0.5, 0.5)` (:298) | actual | `(0.36, 0.34, 0.30)` | `(0.14, 0.14, 0.15)` |
| `structure.tunnelPortal` | n/a | `GRAY` (:535) | actual | `(0.4, 0.4, 0.4)` | `(0.15, 0.15, 0.15)` |
| `structure.terrainWall` | n/a | `GRAY` (:304) | actual | `(0.4, 0.4, 0.4)` | `(0.15, 0.15, 0.15)` |
| `table.board` | fondo negro (`BG_COLOR`, :52) | `(0.4, 0.3, 0.1)` (GraphicPresenter:201) | actual | `(0.28, 0.20, 0.08)` | `(0.10, 0.08, 0.05)` |
| `table.grid` | n/a | `LIGHT_GRAY` (:209) | actual | `(0.5, 0.5, 0.5)` | `(0.18, 0.18, 0.20)` |
| `decor.box` | n/a | `FOREST` (:222) | actual | `(0.2, 0.4, 0.15)` | `(0.08, 0.16, 0.08)` |

### Vía

| Token | 2D actual | 3D actual | Día | Crepúsculo | Noche |
|---|---|---|---|---|---|
| `track.rail` | `BLACK_BRIGHT` (RenderVisitor:45) | `(0.8, 0.8, 0.85)` (:160) | actual | `(0.6, 0.6, 0.68)` | `(0.28, 0.29, 0.35)` |
| `track.rail.inactive` | `BLACK_BRIGHT` (:45) | `(0.1, 0.1, 0.12)` (:165) | actual | igual | `(0.05, 0.05, 0.07)` |
| `track.rail.invalid` | — (2D no lo distingue) | `YELLOW` (:170) | actual | actual | actual (aviso) |
| `track.blocked` | color de la locomotora dueña (RenderVisitor:725–745) | color de la locomotora dueña (TrackRenderer:241) | — | atenuar ×0.8 | atenuar ×0.55 |
| `track.deadEnd` | `YELLOW` (:187) | — | actual | actual | actual (aviso) |

### Elementos

| Token | 2D actual | 3D actual | Día | Crepúsculo | Noche |
|---|---|---|---|---|---|
| `element.station` | `WHITE` (RenderVisitor:47) | color de carga del edificio (`CargoTypes`: GOLD `#FFD800`, COAL `#191919`, RUBY `#FF004C`; ResourceContext:326–331) | actual | actual | atenuar ×0.6 |
| `element.station.selected` | `RED_BRIGHT` (:48) | — (3D resalta con `highlightModel` amarillo) | actual | actual | actual |
| `element.sensor` | `CYAN_BRIGHT` (:46) | `YELLOW` (:272) | actual | actual | atenuar ×0.6 |
| `element.fork` | `WHITE_BRIGHT` (:49) | `GRAY` (:190 / Infra:244) | actual | actual | atenuar ×0.6 |
| `element.fork.selected` | `RED_BRIGHT` (:50) | `WHITE` (:195/232) | actual | actual | actual |
| `element.fork.route` | — | `RED` (:222) | actual | actual | actual |
| `element.semaphore` | `SEMAPHORE_COLOR`/`SELECTED_SEMAPHORE_COLOR` (BLUE/RED_BRIGHT, :56–57) **sin uso** | mástil `GRAY` + placa `BLACK` (:404) | actual | actual | atenuar ×0.6 |
| `element.semaphore.open` | `GREEN` (:54) | luz inferior `GREEN`, superior `440000` (:411–415) | actual | actual | actual |
| `element.semaphore.closed` | `RED` (:55) | luz superior `RED`, inferior `004400` (:411–415) | actual | actual | actual |
| `element.speedSignal.max` | `RED` (:323) | placa `RED` (:358) | actual | actual | actual |
| `element.speedSignal.min` | `BLUE` (:325) | placa `BLUE` (:358) | actual | actual | actual |
| `element.speedSignal.pole` | — | `GRAY` mástil + `WHITE` centro (:350/:376) | actual | actual | atenuar ×0.6 |
| `element.label` | `BLACK_BRIGHT` (:255) | `BLACK` (Infra:162) | actual | actual | actual (sobre placa) |
| `selection.highlight` | subrayado + `WHITE_BRIGHT` (:253…) | `YELLOW` (:185) | actual | actual | actual |
| `selection.line` | — | `GREEN` (:207) | actual | actual | actual |
| `selection.link` | bg `MAGENTA` + fg `BLACK` (:53, :426) | translúcido amarillo/rojo `(1,1,0,0.75)`/`(1,0,0,0.75)` (:35–36) | actual | actual | actual |

Nota: 2D y 3D coinciden en semántica (abierto = verde, cerrado = rojo); el 3D lo hace con dos
esferas (la activa a color, la otra muy oscura). Limpieza pendiente: en 2D `SEMAPHORE_COLOR` y
`SELECTED_SEMAPHORE_COLOR` son constantes muertas.

### Trenes

| Token | 2D actual | 3D actual | Día | Crepúsculo | Noche |
|---|---|---|---|---|---|
| `train.locomotive` | `WHITE` (RenderVisitor:44) | `(0.6, 0.6, 0.6)` por defecto (:180), luego color de jugador (VehicleRenderer:148) | actual | atenuar ×0.8 | atenuar ×0.55 |
| `train.wagon` | `WHITE` (:43) | chasis `(0.5, 0.5, 0.5)` (:218) | actual | atenuar ×0.8 | atenuar ×0.55 |
| `train.cargo.coal` | `WHITE`/`BLACK_BRIGHT` (:700–711) | `#191919` (:328) | actual | atenuar | atenuar |
| `train.cargo.gold` | `YELLOW_BRIGHT`/`YELLOW` (:706) | `#FFD800` (:326) | actual | actual | actual (destaca de noche) |
| `train.cargo.ruby` | `RED_BRIGHT`/`RED` (:708) | `#FF004C` (:330) | actual | actual | actual |
| `train.playerPalette` | `parseColor` (RenderVisitor:757) | `COLOR_PALETTE` (Locomotive:54) | sin cambio | ×0.8 | ×0.55 |
| `train.linkHighlight` | — | translúcido amarillo (:35) | actual | actual | actual |
| `train.unlinkHighlight` | — | translúcido rojo (:36) | actual | actual | actual |
| `train.autoModeDot` | — | `RED` (:277) | actual | actual | actual |
| `train.crash` | `CRASH_COLORS` (:58) | fuegos/humos rojos/naranjas/amarillos (:309–323) | actual | actual | actual (destacan) |

### Cursor

| Token | 2D actual | 3D actual | Día | Crepúsculo | Noche |
|---|---|---|---|---|---|
| `cursor.idle` | — | `YELLOW` (:175) | actual | actual | actual |
| `cursor.drawing` | `GREEN_BRIGHT` (:40) + braille `YELLOW` (:517) | — | actual | actual | actual |
| `cursor.moving` | `YELLOW_BRIGHT` (:41) | — | actual | actual | actual |
| `cursor.erasing` | `RED_BRIGHT` (:42) | — | actual | actual | actual |

### Ambiente 3D

| Token | Actual (GraphicPresenter) | Día | Crepúsculo | Noche |
|---|---|---|---|---|
| `light.ambient` | `AmbientLight (0.5, 0.5, 0.5)` (:190) | `(0.55, 0.55, 0.52)` | `(0.35, 0.28, 0.22)` | `(0.12, 0.14, 0.22)` |
| `light.sun` | `DirectionalLight (0.8, 0.8, 0.8)` dir `(-1, -0.8, -0.2)` (:191) | actual | `(0.7, 0.5, 0.3)` | `(0.25, 0.3, 0.4)` |
| `sky.background` | sin definir (clear negro) | azul cielo `#87CEEB` | `(0.42, 0.27, 0.20)` | `(0.02, 0.03, 0.06)` |
| `sky.fog` | no existe | sin niebla | niebla suave | niebla tenue azulada |
| `light.headlight` | no existía | apagado | `#FFF2C8` atenuado por el ratio | `#FFF2C8`, hasta 4 locomotoras cercanas |

## Reglas y guardarraíles

1. **Determinismo**: la paleta depende solo de `getDayNightRatio()` (ticks), nunca del reloj real.
2. **Contraste mínimo en 2D**: cada variante debe contrastar con el fondo de su franja (familia
   clara: glifos oscuros sobre campo verde de día y claros sobre oscuro de noche); mejor cambiar de
   tono que acercarse al fondo. Las fronteras usan histéresis para no parpadear. Cuando el fondo
   del fundido baja de `MIN_CONTRAST` de luminosidad, el máximo contraste alcanzable es el que
   permite el extremo (negro o blanco); el test exige ese máximo físico.
3. **Los avisos no se apagan**: vía inválida, bloque ocupado, semáforos, señales, cursor y
   resaltados conservan color y contraste de noche (son información de juego, no decorado).
4. **Colores de jugador**: solo atenuación global (≤ 45 % de noche); no se re-mapean a variantes.
5. **Emisivos**: los faros de locomotora son el token `EMISSIVE_HEADLIGHT`, que no se atenúa
   (implementado); farolas y ventanas iluminadas se añadirán como tokens emisivos (1e pendiente).
   Lámpara y luz se encienden a la vez: umbral compartido `VisualPalette.LIGHTS_ON_RATIO` (0,1) y
   rampa `lightsOnFactor` en 3D y 2D.
6. **Rendimiento**: en 3D la paleta se interpola una vez por tick (no por instancia) y los
   materiales actualizan su `ColorAttribute` solo cuando el color cambia; en 2D la paleta se resuelve
   al cruzar un escalón, no en cada frame.

## Fases

| Fase | Alcance | Entregable | Estado |
|---|---|---|---|
| 1a | `VisualPalette` + ambiente 3D (luz, fondo, mesa, rejilla) | El mundo se apaga con `getDayNightRatio()`; test de paleta determinista | Hecha |
| 1b | Terreno (campos, agua, montaña, balasto, túnel, pared) | Tokens de terreno en `VisualPalette` y materiales del `Gdx3DResourceContext`/`GroundRenderer` | Hecha |
| 1c | Elementos, vía y trenes | Tokens de vía/trenes base + atenuación de colores de jugador; avisos intactos | Hecha |
| 1d | 2D: paleta día/noche del terminal (familia clara) | `TerminalPalette` + wiring del `RenderVisitor` | Hecha |
| 1e | Emisivos (faros/farolas) y niebla/cielo fino | **Faros de locomotora** (charco aditivo en 3D + haz simulado en 2D) | Parcial: farolas/ventanas y niebla pendientes; coordinar con #480 |

Pendiente de 1e: farolas/ventanas, acabado de cielo y niebla fina. El resto de la fase 1 está en
`develop`.

### Detalle por fase

- 3D (1a): `VisualPalette` (core, `letrain.palette`) con `AMBIENT_LIGHT`, `SUN_LIGHT`, `SKY` y
  `TABLE_BOARD`; el `GraphicPresenter` los aplica cada tick (luz ambiental, sol direccional según
  `SolarModel`, color de fondo y tablero).
- Vía y trenes 3D (1c): `VisualPalette` gana `TRACK_RAIL`, `TRACK_RAIL_INACTIVE`, `TRAIN_LOCOMOTIVE`
  y `TRAIN_WAGON` (materiales base, atenuados de noche); la librea de jugador, los chasis de vagón
  y el tinte de vía bloqueada se **atenúan** con `playerColorFactor` (1.0 → 0.8 → 0.55) sin
  re-mapearse. Avisos, semáforos, señales, sensores, resaltados, cursor y fuego se quedan como
  estaban (información de juego).
- Horizonte 3D: el fondo se reparte con `glScissor` según la línea de horizonte real (pitch de la
  cámara y FOV): **cielo** por encima y token `VOID` (negro) por debajo, para lo inexplorado. La
  geometría se dibuja encima de ambos, así que la línea solo se ve donde no hay mundo.
- Terreno 3D (1b): `VisualPalette` gana `TERRAIN_FIELDS`, `TERRAIN_WATER`, `TERRAIN_MOUNTAIN`,
  `TERRAIN_BALLAST`, `STRUCTURE_BRIDGE_PILLAR`, `STRUCTURE_TUNNEL_PORTAL`, `STRUCTURE_TERRAIN_WALL`,
  `TABLE_GRID` y `DECOR_BOX` con sus claves día/crepúsculo/noche; `Gdx3DResourceContext.applyTerrainPalette`
  actualiza los materiales (solo cuando el color cambia), el portal de túnel por id de material y las
  paredes de agua del `GroundRenderer` usan el color resuelto del terreno. La rejilla y las cajas de
  decorado del `GraphicPresenter` también siguen su token.
- 2D (1d): `TerminalPalette` (`ui-terminal`, `letrain.visitor.terminal`) con la **familia clara**
  afinada en el laboratorio: día de campo verde, crepúsculo verde apagado, noche oscura; fundido
  con el ratio del reloj, inversión de polaridad y suelo de contraste. El `RenderVisitor` lee el
  ratio una vez por frame (`model.getGameClock().getDayNightRatio()`) y resuelve la paleta solo
  cuando cambia de nivel (ver *Escalonado*); cada token (terreno, vía, estaciones, señales, trenes,
  cursor, resaltados) se pinta con su color y el fondo del mapa es el token `BOARD`.
  **`GROUND` y `BOARD` son el mismo verde** (día `0x4CA331`, crepúsculo `0x527A38`): en 2D las
  celdas de campo se pintan como un espacio sobre el tablero, así que el tablero *es* el terreno
  visible; no existe un marco neutro que conservar. `0x4CA331` es el `TERRAIN_FIELDS` de 3D
  (`0x66994C`) con la misma luminosidad percibida (136) y el mismo tono (~106°), subiendo solo la
  saturación lo justo para que el fallback de 16 colores caiga en el **verde del tema** y no en el
  gris brillante (slot 8). La noche (`0x161923` y sus claves) no cambia, y el **blanco no se usa
  como suelo: queda reservado para la nieve futura** (#692).
  Traducción de color: **24-bit → 256 → 16 ANSI** según `COLORTERM`/`TERM`. El HUD (`menuBox`) y
  el `InfoVisitor` se quedan como estaban.
  **Escalonado**: el ratio solar es continuo y, interpolado, obligaba al terminal a repintar el
  mapa entero cada minuto de juego (parpadeo de 1 Hz en pantalla clara). El cliente cuantiza el
  ratio en **20 niveles** espaciados por luminosidad percibida (`TerminalPalette.BANDS`) con
  **histéresis direccional**: la paleta se resuelve solo al cruzar la frontera del nivel (con
  margen) y las teclas de debug recorren niveles contiguos. El laboratorio sí interpola, que es para
  lo que está.
- Faros (1e parcial): token `EMISSIVE_HEADLIGHT` (constante a cualquier hora). En 3D el
  `GraphicPresenter` dibuja hasta **4 charcos suaves** de suelo (`HeadlightGlows` +
  `HeadlightGlowRenderer`: quad con falloff cuadrático por fragmento y alpha blending) en las
  locomotoras más
  cercanas a la cámara (`Headlights.nearestTo`) con opacidad proporcional al ratio, y el
  `VehicleRenderer` pinta dos lámparas en el frontal que
  **siempre se ven**: emisivas (`headlightModel`) con el motor en marcha y oscuro
  (`headlightOffModel`), apagadas de día o con el motor parado (y en 3D tampoco si está
  descarrilada). Los charcos sustituyen a los `PointLight` originales (#690): la luz por vértice
  sobre el suelo de un quad por celda dibujaba la rejilla como manchas cuadradas, y su plano se
  pinta por encima del balasto (y por debajo de vía y tren) porque las cajas por celda del balasto
  recortaban el charco en agujeros alineados con la rejilla. El charco usa la
  posición **renderizada** (interpolada) que el `VehicleRenderer` publica cada frame
  (`Gdx3DRenderer.getHeadlightSources()`), así se desliza con el tren en vez de saltar de celda en
  celda. Luces y haz solo se encienden con el motor en marcha (`Locomotive.isEngineOn()`), en la
  locomotora de cabeza (`isHeadLocomotive()`) y por encima del umbral compartido. En 2D el
  `RenderVisitor` simula el haz: `Headlight.factor(dx, dy, dir)` da el cono (alcance 3 celdas,
  semiángulo 40°, tope `MAX_LIGHT`), y las celdas que ilumina se pintan mezclando su color nocturno
  con el diurno, **incluido el fondo**, así que el haz "aclara" vía y terreno. El túnel oculto (fuera
  del modo Rails, donde ni tren ni vía se dibujan) también esconde la luz: la locomotora en túnel no
  proyecta haz y las celdas de túnel no se iluminan. Todo el sistema enciende con el mismo umbral
  (`VisualPalette.LIGHTS_ON_RATIO`) y rampa (`lightsOnFactor`), para que lámpara, resplandor y haz
  aparezcan a la vez en ambos clientes.

## Decisiones pendientes

- ¿`VisualPalette` en `core` (`letrain.palette`) o módulo aparte `palette`?
- Configuración del 2D (`terminal.palette=auto|light|dark|theme`), familia oscura y comando de
  consola en caliente: **aplazados**; de momento va siempre la familia clara.
- Contraste de los colores de jugador (locomotoras, carga) sobre el campo verde: hoy se mantienen
  tal cual (los fundidos los ajusta solo el suelo de contraste de los tokens). A revisar cuando se
  juegue en 2D a fondo.
- **Nieve**: el blanco está reservado para un futuro suelo nevado (token/variante propia, sin
  reutilizar el papel); no implementado todavía (#692).
- Curvas de ratio: `SolarModel` sustituyó las franjas fijas 05/07/19/21 por elevación solar con
  banda de crepúsculo; revisar si hace falta una curva propia (más suave) por franja.

## Decisiones tomadas

- **2D: familia clara por defecto** (día de campo verde, noche oscura), elegida tras la demo y
  afinada en `PaletteLab` (tecla `p` para volcar los valores). El fondo del mapa es un token más
  (`BOARD`).
- **2D: el tablero es el terreno, no papel** (#692). `GROUND` y `BOARD` comparten el verde de campo
  (día `0x4CA331`, crepúsculo `0x527A38`), porque las celdas de campo se pintan como un espacio
  sobre el fondo: en 2D no hay marco neutro que conservar. `0x4CA331` replica la luminosidad (136)
  y el tono (~106°) del `TERRAIN_FIELDS` de 3D (`0x66994C`) subiendo solo la saturación necesaria
  para que el fallback ANSI-16 use el verde del tema. **El blanco queda reservado para la nieve
  futura**: ningún suelo lo usa (mientras no exista nieve, el blanco no aparece como terreno).
  La noche no cambia.
- **2D: color 24-bit con degradación 256 → 16 ANSI** (detección por `COLORTERM`/`TERM`, sin
  autodetección del fondo del terminal). Las constantes muertas del 2D
  (`SEMAPHORE_COLOR`, `SELECTED_SEMAPHORE_COLOR`, `SELECTED_FORK_COLOR`,
  `SELECTED_STATION_COLOR`) se limpiaron en el wiring de 1d.
- **HUD/skin fuera** de la fase 1.
