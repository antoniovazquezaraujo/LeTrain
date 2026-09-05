# ADR 016: In-Game Command Line Interface (CLI) y Sistema Modal (Estilo Vim)

## 1. Contexto

LeTrain necesita escalar la forma en que los jugadores interactúan con el mundo. A medida que las redes ferroviarias crecen, el uso exclusivo del ratón para construir vías, asignar trenes o configurar rutas se vuelve lento e ineficiente. 

Adicionalmente, se requiere sentar las bases para la futura programación y automatización de trenes mediante scripts. Por lo tanto, necesitamos un sistema que permita acciones complejas mediante texto (CLI) y que unifique la experiencia gráfica (atajos de teclado) con la consola de comandos.

## 2. Decisión Arquitectónica

Se implementará un motor de comandos basado en **ANTLR4** y un sistema de control de UI basado en un **Patrón Estado (State Machine)** con filosofía estricta tipo Vim.

Esta decisión se divide en las siguientes directrices técnicas:

### 2.1. Separación de Gramáticas (Player vs Scripts)
Para evitar que los scripts en segundo plano puedan alterar la topología del mundo (crear vías, mover el cursor), la gramática ANTLR4 se dividirá en dos ficheros estrictamente separados:
*   **`PlayerCommands.g4`**: Contiene todo el superconjunto de comandos (instanciación `new`, borrado `del`, movimiento de cursor `go`, modos `mode write`). Solo accesible por la terminal interactiva del jugador.
*   **`ScriptLogic.g4`**: Subconjunto restrictivo destinado a la automatización de trenes. Los comandos prohibidos (creación, cursor) generarán un error sintáctico temprano, garantizando la seguridad en tiempo de ejecución.

### 2.2. Interfaz Gráfica como Generador de Texto (Dogfooding)
Se adopta una filosofía de "Dogfooding" absoluto. Los atajos de teclado de la interfaz (tanto en 2D como en 3D) no invocarán métodos del motor de juego directamente (ej. `engine.moveCursor()`).
En su lugar, la interfaz actuará como una capa de macros que **generará comandos de texto puros** y los enviará al parser `PlayerCommands`.
*   *Ejemplo*: Si el usuario está en el modo correspondiente y pulsa `g` seguido de `e`, la interfaz genera el string `"go en"` y lo ejecuta de forma invisible. 
*   *Beneficio*: Garantiza paridad total. Cualquier cosa que se pueda hacer con atajos, se puede automatizar o escribir a mano.

### 2.3. Máquina de Estados Modal (Filosofía Vim)
Para evitar conflictos de teclado (Issue #370 donde `l` se usaba para "Link" y para moverse a la derecha), la captura de inputs utilizará un patrón estado estricto:
*   **`NORMAL_MODE`**: Estado por defecto. Las teclas cambian de modo. (Ej: pulsar `r` entra en `RAILS_MODE`).
*   **Modos Específicos (`RAILS_MODE`, `FORKS_MODE`, etc.)**: Sub-estados donde el teclado tiene funciones dedicadas. (Ej: en `RAILS_MODE`, `h, j, k, l` mueven el cursor, y `w` hace un toggle del lápiz).
*   **`COMMAND_MODE`**: Se entra pulsando `:`. Abre la consola de texto en la parte inferior. Al ejecutar el comando (Enter) o cancelar (Esc), el sistema **siempre** retorna a `NORMAL_MODE`, reseteando el estado anterior.

### 2.4. Resolución de Variables Desacoplada
El jugador podrá guardar variables temporales en su sesión (Ej: `$s1 = new station "Alpha"`). 
*   Estas variables residen en la consola y **solo guardan identificadores (UUIDs o Strings)**, nunca referencias en memoria (`hard references`) a los objetos del motor físico.
*   Si un objeto del motor se destruye (por un choque o por borrado manual), la variable `$s1` queda huérfana. Si el usuario intenta usar `$s1` de nuevo, la consola hará un *lookup* al motor, fallará al no encontrarlo y escupirá un error controlado.
*   *Beneficio*: Acoplamiento cero. El motor físico de LeTrain no necesita saber que la Consola existe ni notificarle la destrucción de entidades para hacer *garbage collection*.

### 2.5. Cursor "Turtle Graphics" y Filosofía UNIX
El cursor del jugador es un ente persistente con modos propios pasados como comandos al motor (`mode write`, `mode move`, `mode del`).
Se soportarán secuencias concatenadas (ej. `20, l, 10` -> avanza 20, gira izquierda, avanza 10).
*   **Ausencia de Rollback**: Siguiendo la filosofía UNIX, si una macro de construcción choca o falla en el paso 15 de 30, **no hay dry-run ni se deshace lo construido**. Se construye hasta el fallo, se cobra el dinero correspondiente, se escupe el error a la consola y se detiene la ejecución.
*   **Responsabilidad del jugador**: El sistema no protegerá al jugador de la bancarrota si ejecuta secuencias masivas sin fondos.

## 3. Consecuencias

### Positivas
*   **Resolución de conflictos UI**: Elimina de raíz problemas como el de la issue #370. El modo normal libera todas las teclas del teclado para comandos rápidos.
*   **Arquitectura Limpia**: Separar las gramáticas y usar la UI como un generador de strings asegura que el Core solo tiene una puerta de entrada para modificar el mundo, simplificando drásticamente el testing.
*   **Escalabilidad**: Preparados desde el día 1 para macros, *blueprints* y carga de scripts externos.

### Negativas / Riesgos
*   **Curva de Aprendizaje**: Los jugadores que no conozcan Vim o no lean la documentación podrían frustrarse si accidentalmente entran en un modo y no saben salir (requiere UX clara que indique "Pulsa ESC para modo normal").
*   **Latencia Perceptible**: Si los atajos de teclado generan texto, lo parsean (ANTLR4) y luego lo ejecutan, hay un pequeñísimo *overhead* vs llamadas directas. ANTLR4 es rápido, pero habrá que vigilar el rendimiento si un jugador deja pulsada una tecla.

## 4. Anexo: Referencia de Modos, Teclas y Comandos

### 4.1. Esquema Modal de Teclas (Hotkeys estilo vim)

Junto a la consola `:`, la interacción directa se organiza en **modos** con filosofía vim: cada modo se entra con una tecla y tiene sus propias acciones. Para que todos los conceptos tengan cabida, algunas teclas actuales se remapean según este esquema.

#### Modos y teclas de acceso (Normal Mode)
En modo `NORMAL`, las teclas de movimiento `h, j, k, l` mueven el cursor. El resto de teclas cambian a los siguientes modos:

| Modo | Tecla |
|---|---|
| Rails | `r` |
| Add | `a` |
| Forks | `f` |
| Semaphores | `m` |
| Signals | `i` |
| Stations | `t` |
| Vehicles | `v` |
| Drive | `d` |
| Couple | `c` |
| Uncouple | `u` |
| Program | `p` |

#### Rails (`r`)

- `[0-9]`: Steps
- `backspace`: Reset steps
- `hjkl`: Move
- `w`: Toggle writing
- `x`: Toggle erasing
- `space`: Invert cursor
- `" #`: Create mark by number
- `" <string>`: Create mark by name
- `g " #`: Go mark by number
- `g " <string>`: Go mark by name
- `g 232,34`: Jump to absolute point
- `g f #`: Go fork #
- `g r #`: Go rail # (Forward # rails)
- `g s #`: Go sensor #
- `g m #`: Go semaphore #
- `g i #`: Go signal #
- `g t #`: Go station #
- `g l #`: Go locomotive #
- `g[np] r`: Go next/prev rail (Advance until it reaches another rail)
- `g[np] f`: Go next/prev fork
- `g[np] s`: Go next/prev sensor
- `g[np] m`: Go next/prev semaphore
- `g[np] i`: Go next/prev signal
- `g[np] t`: Go next/prev station
- `g[np] l`: Go next/prev locomotive
- `g e`: Go to end of railway
- `r [nswe]`: Rotate towards that direction
- `r [-][1-7]`: Rotate that grades
- `f [fsmitl] #`: Face to fork, sensor, semaphore, signal, station or locomotive
- `.`: Repeat last action

#### Add (`a`)

- `s`: sensor
- `m`: semaphore
- `i`: signal
- `t`: station

#### Forks (`f`)

- `⏴,⏵`: Select · `o`: Locate · `Space`: Toggle · `#`: Select by ID

#### Semaphores (`m`)

- `⏴,⏵`: Select · `o`: Locate · `Space`: Toggle · `#`: Select by ID

#### Signals (`i`)

- `⏴,⏵`: Select · `o`: Locate · `Space`: Invert · `m`: Max/Min · `⏶,⏷`: Limit

#### Stations (`t`)

- `⏴,⏵`: Select · `o`: Locate · `#`: Select by ID

#### Vehicles (`v`)

- `[A-Z][0-9]`: Locomotive and color · `[1-3][a-z]`: Wagon type and letter · `Enter`: Finish

#### Drive (`d`)

- `⏴,⏵`: Select · `o`: Locate · `Space`: Reverse · `m`: Motor On/Off · `⏶`: Accel · `⏷`: Decel · `Enter`: Load/Unload · `#`: Select by ID

#### Couple (`c`)

- `⏶,⏷`: Front/Back · `⏴,⏵`: Select/Unselect wagons · `o`: Locate · `Space`: Couple

#### Uncouple (`u`)

- `⏶,⏷`: Front/Back · `⏴,⏵`: Select/Unselect wagons · `o`: Locate · `Space`: Uncouple

#### Program (`p`)

- Muestra el IDE de scripts.

#### Equivalencias con la consola `:`

Las teclas modales son abreviaturas directas de comandos de la consola. Mientras la CLI usa alias de dos letras (`fo`, `sn`, `st`...), el esquema modal usa una sola letra por tipo (fork, rail, sensor, semaphore, signal, station, locomotive):

| Modal | Consola `:` | Texto |
|---|---|---|
| `g f #` | `go fo #` | `go fork #`|
| `g s #` | `go sn #` | `go sensor #`|
| `g m #` | `go sm #` | `go semaphore #`|
| `g i #` | `go si #` | `go signal #`|
| `g t #` | `go st #` | `go station #`|
| `g l #` | `go lo #` | `go locomotive\|train #`|
| `g[np] r` | `gn ra \| gp ra` | `go next\|prev rail`|
| `g[np] f` | `gn fo \| gp fo` | `go next\|prev fork`|
| `g " #` | `go "#` | `go mark #`|
| `g e` | `go en` | `go end`|
| `r [nswe]` | `face n\|s\|w\|e` |`face north\|south\|west\|east`|
| `f [fsmitl] #` | `face [fsmitl] #` | `face fork\|sensor\|semaphore\|signal\|station\|locomotive`|

### 4.2. Retroalimentación de Comandos (Eco)

- Si un comando no puede ejecutarse, se muestra un **error en la consola**.
- Si un comando puede ejecutarse pero no termina nunca (p. ej. `g e` en una vía que es un circuito cerrado), el usuario debe poder **cancelarlo con `Esc`**.
- El board muestra: el **modo actual** ("Writing", "Erasing", etc.), el **comando que se está tecleando** (p. ej. `g n ...` mientras espera a que se complete) y una **descripción** de lo que hará.
- `.` repite la última acción (comando punto).

---

## 5. Anexo: Diccionario de Comandos y Ejemplos

### Abreviaturas de Objetos (Nota de Diseño)
Todos los objetos físicos tienen un alias estricto de dos letras para agilizar la escritura en comandos complejos:
* `fo` (fork)
* `ra` (rail)
* `sn` (sensor)
* `sm` (semaphore)
* `si` (signal)
* `st` (station)
* `tr` (train)
* `wg` (wagon)

El esquema modal (sección 4.1) usa una sola letra por tipo: `f` (fork), `r` (rail), `s` (sensor), `m` (semaphore), `i` (signal), `t` (station), `l` (locomotive).

### Control de Trenes
* `train 1 start motor`: Arranca el motor.
* `train 1 set speed 3`: Establece la velocidad a 3.
* `train 1 stop`: Detiene el tren.
* `train 1 forward to contact`: Avanza a velocidad de maniobra hasta chocar y se detiene.
* `train 1 link forward all`: Ordena acoplar vagones.
* `train 1 invert`: Invierte la marcha del tren.

### Navegación de Cursor
* `go 232,34`: Salto a coordenadas absolutas.
* `go "1`: Ir a la marca 1.
* `gn fo`: Alias de "go next fork".
* `gn ra`: Alias de "go next rail" (Avanza hasta otra vía. Si estás en una avanza 1).
* `gn sn`: Alias de "go next sensor".
* `gn sm`: Alias de "go next semaphore".
* `gn si`: Alias de "go next signal".
* `gn st`: Alias de "go next station".
* `gp fo`: Alias de "go prev fork".
* `g st 1`: Alias de "go station 1".
* `g tr 1 h`: Alias de "go train 1 head".
* `ge`: Alias de "go to end" (Avanza hasta el final de la vía).

### Rotación de Cursor (Mirar hacia)
* `face n`: El cursor pivota para mirar al Norte.
* `face s`: El cursor pivota para mirar al Sur.
* `f n`: Alias de "face n".
* `face st 1`: El cursor pivota hacia la posición de la estación 1.

### Construcción y Navegación (Secuencias Turtle)
Las secuencias de Turtle Graphics se ejecutan mediante verbos *sin estado* que procesan la cadena de movimientos entera y devuelven al usuario al modo de juego normal. Se pueden encadenar números (pasos) y letras `l` o `r` (giros). Los giros seguidos (ej. `l, l`) asumen un avance implícito de 1 paso entre ellos.
* `write 20, l, 3, r, 32, "1, l, l, 32`: Entra en modo construcción y construye toda la secuencia de golpe a partir del cursor.
* `move 14, l, 2`: Se desplaza por la red siguiendo la secuencia sin construir vías.
* `del 5, l, 3`: Funciona como goma de borrar, avanzando por el mapa y eliminando vías y objetos a su paso.
* `clear 20, r, 5`: Pasa el "triturador" siguiendo la secuencia (borra vehículos y accesorios, deja intacta la vía).
* `gn fo, r, ge`: Avanza al próximo desvío, gira a la derecha y avanza hasta el final de la vía (combinación con navegación topológica).

### Creación / Asignación
* `new st 1`: Crea la estación 1.
* `$s1 = new st "Alpha"`: Crea la estación "Alpha" y la asigna a `$s1`.
* `new sm 1`: Crea el semáforo 1.
* `new sn 2`: Crea el sensor 2.

### Eliminación (Contextual y Directa)
* `del st 1`: Borra la estación 1.
* `del $s1`: Borra la entidad asociada a `$s1`.
* `del`: Borra la entidad bajo el cursor.
* `del tr here`: Elimina el tren entero en el cursor.
* `del wg here`: Elimina el vagón específico bajo el cursor.
* `del tr "Expreso"`: Elimina el tren buscándolo por nombre.

### Gestión del Mundo
* `new`: Reinicio total.
* `new keep-map`: Borra entidades, conserva el terreno.
* `new map mountains=high rivers=low gold=high`: Genera un nuevo mapa procedural.
* `w` (o `save`): Guarda la partida actual.
* `q`: Cierra la consola.
* `wq`: Guarda la partida y cierra la consola.


* `save "partida1"`: Exporta la partida a JSON.
* `load "partida1"`: Carga el JSON.
* `game stop`: Botón del pánico global (pausa/parada de emergencia).

### Manipulación Lógica y Marcas
* `sm 1 open`: Abre el semáforo 1.
* `fo set left`: Cambia la aguja del desvío actual a la izquierda.
* `mark 1`: Guarda la coordenada actual en `"1`.
* `mark central_hub`: Guarda una marca de texto (usable luego con `go central_hub`).

---

## 6. Roadmap de Implementación (Fases MVP)

1.  **Fase 1 (Administración Directa):** Integrar ANTLR4 con Java. Solo comandos directos (`train 1 stop`, `save`, `new`).
2.  **Fase 2 (Navegación Topológica):** Crear el Cursor. Parsear secuencias de movimiento simples y comandos de navegación (`go 10,20`, `gn fo`, `g st 1`).
3.  **Fase 3 (Lápiz y Goma - Turtle Graphics):** Comandos secuenciales (`write`, `move`, `del`, `clear`) para modificar el mapa mediante cadenas `write 20, l, 3`.
4.  **Fase 4 (Memoria y Variables):** Tabla de símbolos. Guardado JSON. *String Lookup* y resolución UPSERT. (`$s1 = new...`).
5.  **Fase 5 (Motor de Macros - Opcional):** Ejecución de scripts largos en ficheros externos (`:load script.letrain`).
6.  **Fase 6 (Hotkeys Modales):** Esquema modal de la sección 3.8: parser de prefijos `g` / `g[np]`, marcas `"`, modos Add/Vehicles/Drive, eco de comandos (3.9) y comando `.`.
