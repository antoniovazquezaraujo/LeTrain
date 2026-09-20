# Mapa de colores día/noche (fase 1 de ADR-022)

Estado: **propuesta para revisión** (inventario previo a tocar código)

Relacionado: [[ADR-022-Game-Time]] (fase 1), [[ADR-013]] (modo noche/día), issue #480
(paridad 2D/3D).

## Objetivo

Antes de implementar el ciclo día/noche hay que saber **qué color cambia cada cosa y dónde
vive**. Hoy los colores están repartidos en literales por los renderers de los dos clientes; sin
un mapa único, ajustar la noche es tocar veinte sitios a ciegas.

Alcance de la fase 1: **mundo** (terreno, vía, elementos, trenes, ambiente). El HUD/skin queda
fuera (solo se revisará su legibilidad al final).

## Cómo funciona hoy

- **Reloj**: `GameClock.getDayNightRatio()` (0.0 = pleno día, 1.0 = noche cerrada) e `isNight()`
  (ratio > 0.5). Transiciones actuales: día 07:00–19:00, crepúsculo de 19:00 a 21:00, noche
  21:00–05:00, amanecer de 05:00 a 07:00.
- **3D**: materiales con `ColorAttribute.createDiffuse(...)` fijos + `Environment` global
  (`AmbientLight` 0.5 gris y `DirectionalLight` 0.8). No hay cielo, niebla ni color de fondo
  configurable (el *clear* es el negro por defecto). Como todo el mundo es difuso y recibe la luz,
  **oscurecer la luz ambiental ya apaga el mundo entero**; encima harán falta retoques por token.
- **2D**: colores ANSI fijos en `RenderVisitor` (constantes y literales) sobre fondo negro
  (`BG_COLOR`). La paleta ANSI es discreta (16 colores): aquí no se interpola, se elige variante
  por franja con **histéresis** en las fronteras.

## Arquitectura propuesta

1. **`VisualPalette`**: única fuente de verdad. Un enum/registro de **tokens** (p. ej.
   `terrain.fields`, `track.rail`, `light.ambient`) con hasta tres variantes: `day`, `dusk`,
   `night` (RGB 0–1). Propuesta de ubicación: `letrain.palette` en `core` como **datos puros**
   (sin libgdx ni Lanterna); cada cliente los traduce a su tecnología (ANSI en 2D, `Color` en 3D).
   Si crece con detalle de UI, se mueve a un módulo propio.
2. **3D**: `palette.colorOf(token, dayNightRatio)` interpola entre variantes (día→crepúsculo→
   noche). Los materiales dejan de usar literales: piden el token. La luz ambiental, la
   direccional y el color de fondo/niebla también son tokens.
3. **2D**: `palette.ansiOf(token, band)` con franjas (día/crepúsculo/noche) e histéresis ±5 min
   de juego alrededor de cada frontera; el terminal no puede interpolar.
4. Los colores **de jugador** (paleta de locomotoras, carga) no se rediseñan: se atenúan con un
   multiplicador global de la luz (3D) o con la variante ANSI más cercana (2D).

## Inventario (el mapa)

Convención: `n` = `[0–1]` por canal. 2D en colores ANSI de Lanterna.

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
| `sky.background` | sin definir (clear negro) | negro actual | `(0.25, 0.18, 0.12)` | `(0.02, 0.03, 0.06)` |
| `sky.fog` | no existe | sin niebla | niebla suave | niebla tenue azulada |

## Reglas y guardarraíles

1. **Determinismo**: la paleta depende solo de `getDayNightRatio()` (ticks), nunca del reloj real.
2. **Contraste mínimo en 2D**: ninguna variante por debajo de `BLACK_BRIGHT` sobre fondo negro;
   preferir cambiar de color ANSI antes que oscurecer a negro. Las fronteras usan histéresis para
   no parpadear.
3. **Los avisos no se apagan**: vía inválida, bloque ocupado, semáforos, señales, cursor y
   resaltados conservan color y contraste de noche (son información de juego, no decorado).
4. **Colores de jugador**: solo atenuación global (≤ 45 % de noche); no se re-mapean a variantes.
5. **Emisivos**: faros de locomotora, farolas y ventanas iluminadas se añaden como tokens
   "emisivos" que no se atenúan (fase 1e); son la guía visual de noche.
6. **Rendimiento**: interpolar la paleta una vez por frame (no por instancia); los materiales
   actualizan su `ColorAttribute` solo cuando el token cambia.

## Fases propuestas

| Fase | Alcance | Entregable |
|---|---|---|
| 1a | `VisualPalette` + ambiente 3D (luz, fondo, mesa, rejilla) | El mundo se apaga con `getDayNightRatio()`; test de paleta determinista |
| 1b | Terreno (campos, agua, montaña, balasto, túnel, pared) | Terreno con variantes por token |
| 1c | Elementos, vía y trenes | Materiales por token; avisos intactos |
| 1d | 2D: variantes ANSI por franja con histéresis | Terminal con día/crepúsculo/noche |
| 1e | Emisivos (faros/farolas) y niebla/cielo fino | Noche con guías de luz; coordinar con #480 |

## Decisiones pendientes

- ¿`VisualPalette` en `core` (`letrain.palette`) o módulo aparte `palette`?
- ¿Se limpian las constantes muertas del 2D (`SEMAPHORE_COLOR`, `SELECTED_SEMAPHORE_COLOR`) en
  esta fase o en un PR aparte?
- ¿El fondo del terminal cambia de noche (p. ej. azul muy oscuro) o se queda negro?
- ¿Curvas de ratio propias por franja (más suaves) o las actuales 05/07/19/21?
