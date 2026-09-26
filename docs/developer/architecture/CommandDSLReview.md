# Revisión del DSL de comandos: sintaxis y coherencia

**Fecha:** 2026-09-26
**Estado:** documento de trabajo para decisiones de diseño. **No modifica código.**
**Autoría:** inventario de Alex, revisión crítica de Bicho, contraste con las gramáticas por Dani.

## Objetivo y método

Este documento hace un inventario completo de la sintaxis del lenguaje de comandos de
LeTrain y una revisión crítica de su coherencia, para decidir con calma qué se simplifica
y qué no, **antes de tocar nada**. Los hallazgos marcados «verificado» se reprodujeron con
sondas desechables (fuera del repo); el resto es lectura directa de código.

Fuentes de verdad:

- Gramáticas: `core/src/main/antlr4/letrain/command/LeTrainLexer.g4`,
  `PlayerCommandsParser.g4`, `ScriptLogicParser.g4`.
- Semántica: `CommandManager`, `PlayerCommandExecutor`, `AutomationEngine`,
  `ScenarioCompiler`, `GrammarReference`.
- Usuario: `docs/user/grammar*.md`, `cheatsheet*.md`, `scenarios*.md`.

## 1. Puntos de entrada

| Entrada | Qué parsea | Notas |
|---|---|---|
| Consola (2D/3D) | `PlayerCommandsParser`; cada sentencia-script se **re-parsea** con `ScriptLogicParser` en un `CommandManager` **nuevo** | `create itinerary` + `assign` **no funciona** desde consola (issue #632). La consola añade un `;` final si falta. `help` es textual y solo como primera palabra |
| Bloque `program { }` (guion, editor pestaña Program, partida guardada) | `ScriptLogicParser` con un único `CommandManager` | **Pasa todo a minúsculas, strings incluidos** (`AutomationEngine`); ante error de sintaxis **sigue ejecutando**; los errores del lexer se pierden |
| `on build` / `on start` | línea a línea con `PlayerCommandsParser` | |
| Editor pestaña Scenario / `letrain-check` | `ScenarioCompiler` | Solo valida |
| Ayuda / referencia (`GrammarReference`) | — | **No se valida** contra las gramáticas: contiene ejemplos inválidos (ver §5) |

## 2. Inventario de sintaxis

### 2.1 Sentencias compartidas (`ScriptLogicParser.g4`)

```
statement      : trigger commandBlock SEMI?
               | createItinerary SEMI?
               | directCommand SEMI
directCommand  : assignItinerary | setAutopilot | setNameCommand
               | directTrainCommand | directForkCommand | directSemaphoreCommand
               | directSignalCommand | directStationCommand | directSensorCommand
```

- `;` obligatorio en órdenes directas; **opcional** tras `}` de trigger y tras cada waypoint.
- `PlayerCommandsParser` añade los comandos de consola (con `;` obligatorio).

### 2.2 Trenes

`train <n | "nombre">` + una acción:

- Sentido: `set forward` / `set backward`.
- Velocidad: `set speed N` (el atajo `set N` se **elimina** — U1, §7b) · `accelerate` ·
  `decelerate`.
- Girar sentido: `invert` o `reverse`, en todas partes (U2, §7b).
- Maniobras: `couple forward|backward [N|all]` · `uncouple forward|backward [N|all]`.
- Nombre: `set name "X"`.
- Motor: `set engine on|off`.
- Carga: `load` · `unload`.
- Misiones: `stop at station|sensor <ref>|end [speed N]` · `stop when blocked [speed N]` ·
  `stop on contact [speed N]`.

**No existen hoy**: `train N stop;` ni `park` suelto (U3 lo añade); `train N reverse;`
(U2 lo añade como alias de `invert`).
`ref` = número o **nombre entrecomillado**; la idea es que los nombres valgan en **todas**
las referencias, triggers y `train at` incluidos (U4, §7b).

### 2.3 Misiones (`stopOrder`, issue #619)

- `stop at …` — destino estación/sensor (por número o nombre) o `end`.
- `stop when blocked` — avanza hasta la frontera del cantón bloqueado.
- `stop on contact` — aproximación de enganche (issue #645).
- `speed N` opcional y **detrás** del destino; sin `speed`, se usa la velocidad actual.
- Orden suelta: auto-invierte una vez si el destino está detrás. Acción de waypoint:
  **no** auto-invierte (hay que escribir el `reverse`).
  Punto confuso a unificar: ver **U5** (§7b).

### 2.4 Maniobras

```
coupleAction   : COUPLE sense vehicleCount?
uncoupleAction : UNCOUPLE sense vehicleCount?
vehicleCount   : NUMBER | ALL
```

- `sense` obligatorio (`forward|fw`, `backward|bw`).
- Sin contador: `couple` engancha **todo**; `uncouple` desengancha **1**. Propuesta: que
  `uncouple` sin número desenganche **todo**, igual que `couple` (**U6**, §7b), y definir
  qué significa `0`.
- `all` = centinela (`Integer.MAX_VALUE`); `all` es palabra reservada (documentado).

### 2.5 Infraestructura (directo y en bloques)

- Aguja: `fork N set dir|straight|curved|flip` · `fork N flip` (`dir` = `e|ne|n|nw|w|sw|s|se`).
- Semáforo: `semaphore N open|closed|close` · `semaphore N set open|closed` · `semaphore N invert`.
- Señal: `signal N set limit X` · `signal N set mode max|min` · `signal N invert`.
- Estación/sensor: `station N invert` · `sensor N invert`.

### 2.6 Itinerarios y waypoints

```
create itinerary "x" { waypoint* }
assign itinerary "x" to train <n>
train <n> set autopilot true|false

waypoint : add station|sensor <ref> [dirección] [arrival H:MM] , acciones , [departure H:MM]
```

- Orden **obligatorio**: `arrival` → acciones (en orden de escritura) → `departure`.
- Comas **obligatorias** entre ítems del plan; la referencia y su dirección **no** llevan coma.
  Propuesta: coma también entre `<ref> [dirección]` y el primer atributo, para una regla
  uniforme (**U7**, §7b).
- `;` opcional tras cada waypoint y tras `}`.
- Mínimo **2 waypoints** (validación de ejecución, no de gramática): un itinerario es un bucle.
- Acciones: `load`, `unload`, `reverse`, `stop`, `park`, `wait N`, `speed N`,
  `couple`/`uncouple`, `stop at/blocked/contact`, `fork N set straight|curved|flip`.
- `park`: frena, apaga motor y **mantiene** el autopilot; sin salida programada posterior
  queda aparcado hasta que algo lo arranque. `stop`: frena y **desactiva** el autopilot.

### 2.7 Triggers y bloques

```
<sensor|fork|semaphore> N on train [enter|exit|couple|uncouple] [fw|bw] { … }
station N on (train evento | evento train) { … }
train [N] on crash|contact [fw|bw] { … }
```

Dentro del bloque: acciones de aguja/semáforo, o `train [N]` / `train at <sitio> N` + acción
de tren; cada ítem con `;`. `train at` es un **token con un espacio** (`'train at'`).

### 2.8 Comandos de consola

`go/g`, `gn/gp`, `face`, `new`, `del`, `clear`, `slide`, `mark/m`, `ls`, `info`,
`save/load/export/import`, `journal`, `undo/redo`, `time`, `quit/q/q!/wq`,
tortuga (`write/move/del/clear`), `help`.

### 2.9 Léxico

- Alias: `tr`, `st`, `sn`, `fk`, `sm`, `sg`, `fw`, `bw`, `rl`, `loco`, `g`, `m`…
- **Muchas palabras reservadas** (`all`, `speed`, `station`, direcciones `n/s/e/w/ne…`,
  colores, `time`, `end`, `mode`, `limit`…) que rompen identificadores desnudos.
- `STRING "…"` sin escapes y admite saltos de línea.
- `NUMBER` entero con signo opcional; sin decimales.
- `TIME H:MM` / `HH:MM` estricto en waypoints. Propuesta: admitir también `HH` sin minutos
  (`arrival 9` = 09:00) (**U8**, §7b).
- Sensibilidad a mayúsculas: keywords en minúsculas; consola case-sensitive;
  `program` pasa **todo** a minúsculas (también los strings).

## 3. Hallazgos de coherencia

### 🔴 Rompen flujos que muestran los manuales

1. **Consola: `create itinerary` + `assign` no funciona** (un `CommandManager` nuevo por
   sentencia; «itinerary not found» incluso en una sola línea). Documentado en guías y
   chuletas. Issue **#632**.
2. **`program` pasa todo a minúsculas (strings incluidas)** y los nombres se buscan
   case-sensitive → waypoints/itinerarios **desaparecen en silencio**. (Verificado.)
3. **`program` ejecuta el resto aunque haya error de sintaxis**; los errores de lexer no se
   ven; consola y `letrain-check` son fail-fast. Tres políticas para el mismo error.
   (Verificado.)
4. **NPE en `semaphore N invert|open|closed|close` dentro de bloques** (hay que usar
   `set open`). Gramática acepta, ejecución revienta. (Verificado.)

### 🟠 Sintaxis muerta o no-op silencioso

5. Eventos `couple/uncouple` en triggers **nunca disparan**; el `sense` de `crash/contact`
   se ignora. (Verificado.)
6. Tokens `LEFT/RIGHT` sin uso; la ayuda muestra `fork set left/right` (no parsea) y
   `train # on link/unlink` (no existen tokens). Ramas muertas asociadas.
7. `train N set name "X"` **dentro de bloque** = no-op; `st/sn … set name` = no-op;
   renombrar semáforo: el mensaje de rechazo se descarta. (Verificado.)
8. Entidades inexistentes en órdenes directas = **silencio**; triggers con selector
   inexistente = **silencio**. (Verificado.)
9. Waypoint con destino inexistente: **se descarta sin aviso** (y si quedan ≥2, el
   itinerario se crea igual). (Verificado.)
10. Clamps y normalizaciones sin aviso: `set speed 99` → 10; `set speed -4` → 0;
    `time set 25:99` → wrap silencioso del reloj. (Verificado.)
11. Tortuga: pasos `R`/identificador ignorados; `del 1` es tortuga, no entidad;
    `info 5` ignora el número. (Verificado.)
12. **Los avisos de maniobras de itinerario no llegan a pantalla** (el notifier solo se
    instala para órdenes sueltas), contra lo que promete la documentación.

### 🟡 Asimetrías a decidir (confunden aunque «funcionen»)

13. Girar sentido: `reverse` (waypoint) / `invert` (directo) / `set forward|backward`;
    auto-reverse solo en órdenes sueltas. → **U2** y **U5** (§7b).
14. `couple` sin número = todos; `uncouple` sin número = 1; y `0` significa «todos» en
    couple pero «1» en uncouple. → **U6** (§7b).
15. `fork N set <dirección>` tiene **tres comportamientos**: consola no-op si no mapea,
    trigger hace `flip`, waypoint mapea.
16. `train at` es un token con un **espacio exacto**: doble espacio o tab lo rompen.
17. Trigger de estación admite orden invertido (`station 1 on enter train`), indocumentado.
18. Colisión de ids sensor/señal: `stop at sensor N` puede apuntar a una señal de velocidad
    y no completar nunca.
19. Ayuda/chuletas con ejemplos que **no parsean**: `face dir_n`, `fork set left/right`,
    `signal N limit N`, `train N reverse`, `train # stop`, `train at … stop`, y `#` como
    comentario dentro de `program`.

## 4. Taxonomía de casos de error

- **A. Error de sintaxis** — consola fail-fast; `program` sigue a medias; tres mensajes
  distintos para el mismo error.
- **B. Rechazo semántico con aviso** — consola sí (mensaje visible); **en el juego, solo
  log** salvo órdenes sueltas.
- **C. No-op silencioso** — la familia grande (hallazgos 5-12).
- **D. Fallo de runtime/misión** — choque, ruta perdida, stall: avisan solo si hay notifier
  (las maniobras de itinerario no lo tienen).
- **E. Espera indefinida** — bloque, `park`, `wait`, retención… por diseño; ojo:
  `stop on contact` sin vehículo delante puede no terminar nunca.
- **F. Éxito silencioso** — por diseño (decisión UX de #619).
- **G. Validación que no valida** — p. ej. la sección `configuration` de escenarios no se
  compila (cualquier `key=value` pasa el checker).

Lo más preocupante: **C** y la **asimetría de canales** (lo mismo avisa en consola y calla
en el juego).

## 5. Discrepancias doc/ayuda ↔ gramática

| Forma documentada | Realidad |
|---|---|
| `face dir_n;` | Solo direcciones desnudas: `face n;` |
| `fork <id> set left/right;` | No existe en `forkDirection` (tokens muertos) |
| `signal <id> limit <n>;` | Falta `set`: `signal N set limit X;` |
| `train <id> reverse;` | El directo es `train N invert;` |
| `write 5, l, m marca, …` | Los pasos con identificador/marca se ignoran en silencio |
| `<entidad> "viejo" set name "nuevo";` | Semáforos/agujas/señales no admiten nombre; `st/sn` no renombran |
| `train # stop;`, `train at … stop;` | No parsean (la ayuda los muestra) |
| `#` comentario también dentro de `program` | Dentro de `program` un `#` es error de sintaxis |
| `fork 1 set left/right` en chuletas | Error de sintaxis (verificado) |

`GrammarReference` se autodeclara «single source of truth» pero ningún test lo valida
contra las gramáticas.

## 6. Huecos de cobertura (tests)

No hay tests de: consola `create`+`assign`; `st/sn set name`; ejecución parcial del
programa; `semaphore … invert/open` en bloque; eventos `couple`; `time set` fuera de rango;
`train  at` con dos espacios; colisión de ids en `stop at sensor`; tortuga con mayúsculas;
validación de `GrammarReference`/chuletas. Son los huecos que explican que todo esto esté
verde en `mvn test`.

## 7. Decisiones a tomar (registro)

- **D1. Política de errores y silencios** — **CONFIRMADA (2026-09-26)**.
  Principio: «todo problema avisa; el éxito calla», con tres niveles: *sintaxis* → mensaje
  visible y no ejecutar (validar antes de ejecutar); *rechazo semántico* → aviso visible;
  *ajustes mecánicos* → avisar o dejar de ajustar. El log nunca es el único canal de un
  problema. El detalle caso a caso se cierra en D4.
- **D2. Unificación consola ↔ programa** — PENDIENTE.
  ¿Un solo comportamiento para el mismo texto (case, fail-fast, avisos)? ¿Se arregla
  `create`+`assign` en consola (#632)? ¿Qué pasa con el lowercasing y los strings?
- **D3. Saneamiento de sintaxis** — PENDIENTE.
  ¿Se retira la sintaxis muerta (`LEFT/RIGHT`, `link/unlink`, eventos `couple`, `stop`
  suelto en ayuda)? ¿Se alinean ayuda y chuletas y se validan con un test?
  Incluirá las decisiones **U1–U8** (§7b) una vez confirmadas.
- **D4. Casos de error, uno a uno** — PENDIENTE.
  Recorrer la lista de §4 y decidir el comportamiento esperado de cada caso.

## 7b. Decisiones de sintaxis propuestas (anotadas por el usuario, 2026-09-26)

Notas del usuario sobre esta revisión, pasadas a propuestas numeradas (su texto, literal,
entre comillas). Estado: **pendientes de confirmar** salvo indicación.

- **U1. Fuera el atajo `set N`** — «VAMOS A PROHIBIR LO DE set N, que ponga siempre "speed"».
  Siempre `set speed N`. Coste: bajo (gramática, docs y tests). Sin compatibilidad legacy.
- **U2. `invert` y `reverse` como sinónimos** — «invert|reverse» / «permitamos invert ó reverse».
  Aceptar ambos en orden directa y en waypoint; hoy `reverse` es acción de waypoint e `invert`
  orden directa, y unificar elimina esa asimetría. Coste: bajo.
- **U3. `park` como orden directa** — «agreguemos park». `train N park;` con la misma semántica
  que la acción de waypoint (frena, apaga motor, mantiene autopilot). Coste: bajo.
- **U4. Nombres en todas las referencias** — «Permitamos nombre también». Triggers, `train at`
  y selectores aceptan nombre además de número. Coste: medio. **Depende de D2** (política de
  mayúsculas/nombres: hoy `program` pasa todo a minúsculas y los nombres se buscan
  case-sensitive).
- **U5. Unificar el auto-reverse** — **DECIDIDA (2026-09-26): (b) auto-giro en ningún sitio.**
  Todo explícito: también las órdenes sueltas exigirán girar el tren a mano (`invert`/`reverse`)
  antes de ir a un destino que está detrás. Sin giros automáticos en ningún contexto.
- **U6. `uncouple` sin número = todo** — «unifiquemos esto: uncouple sin número desengancha todo».
  Iguala el default de `couple`; además, definir qué significa `0` (hoy: todos en couple, 1 en
  uncouple). Coste: bajo.
- **U7. Coma también tras `<ref> [dirección]`** — «Me parece que entre la <ref> [dirección] y
  arrival debería ir también coma». Regla uniforme: cada ítem del plan separado por comas; la
  ref+dirección es un bloque. Ej.: `add station 1 ne, arrival 10:00, uncouple backward all,
  departure 10:30;`. Coste: bajo-medio (gramática, ejemplos, docs y tests). Sin legacy.
- **U8. Horas sin minutos** — **DECIDIDA (2026-09-26)**: admitir `HH` sin minutos en waypoints
  (`arrival 9` = 09:00) y también en `time set` (`time set 9` = 09:00). Además, `time set`
  fuera de rango (`25:99`) pasa de normalizar en silencio a **error con aviso** (D1).

## 8. Principios propuestos (para discutir)

1. **Todo problema avisa** (consola del juego); el éxito calla. Sin silencios para: entidad
   inexistente, waypoint descartado, orden rechazada, evento muerto, valor recortado.
2. **Un texto, un comportamiento**: o se unifican los puntos de entrada, o cada diferencia
   se documenta y se justifica.
3. **No dejar sintaxis que parsea y no hace nada**: o se implementa, o se retira de
   gramática y ayuda.
4. **La ayuda y las chuletas se validan contra la gramática** en un test (evita promesas
   que no parsean).
