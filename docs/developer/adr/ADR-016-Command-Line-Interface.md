# ADR-016: Interfaz de Línea de Comandos (CLI), Hotkeys Modales y Modo de Automatización

## 1. Contexto

**Interfaz de Usuario (Hotkeys):** Al igual que en la consola clásica de UNIX, la pulsación de la tecla `:` durante el juego abrirá un *prompt* en la parte inferior de la pantalla. Todos los comandos (ej. `w`, `wq`, `new st 1`) se teclearán tras esos dos puntos iniciales.
*   **Historial de Comandos:** Pulsar las flechas Arriba/Abajo mientras el prompt está abierto navegará por el historial de los últimos comandos introducidos, permitiendo recuperar y editar secuencias largas fácilmente.


El simulador LeTrain requiere un sistema avanzado para la construcción masiva y la automatización de la red ferroviaria. Se ha propuesto la implementación de una consola de comandos (CLI) que siga la filosofía de herramientas UNIX y `vi`, permitiendo interacciones rápidas basadas en texto (Turtle Graphics).

El reto principal radica en integrar un intérprete de comandos que interactúe de forma segura con el motor físico, el renderizado y el motor económico del juego.

## 2. Decisión

Se desarrollará un lenguaje de dominio específico (DSL) y un *Command Interpreter* propio mediante **ANTLR4**.
Descartamos lenguajes genéricos (Lua, Python) para mantener la experiencia de usuario ultra-rápida y la filosofía de atajos (`gn fo`, `go m-1`) que hace única a esta consola.

El desarrollo se abordará mediante un enfoque iterativo por fases (MVP).

---

## 3. Especificación Arquitectónica y Resolución de Problemas

### 3.1. Separación Hardware vs. Software (Ciclo de Vida)
Si un script crea trenes y se ejecuta dos veces, ¿se llena el juego de trenes clonados destruyendo la economía? Para evitar esto:
*   **Capa Hardware (Física y Persistente):** Vías, estaciones, sensores, trenes. Son persistentes. Una vez construidos (por ratón o consola), se quedan en el mapa generando dinero.
*   **Capa Software (Lógica Volátil):** Triggers (`on sensor X...`), itinerarios. Residen en el **Modo Program** (Editor de scripts). **Se prohíbe crear, destruir o navegar físicamente en esta capa**. Es decir, los comandos de Cursor (`go`, `gn`, `mode...`) y los de instanciación/borrado (`new`, `del`) están vetados en los scripts en segundo plano. Así, cada vez que el jugador da a "Aplicar", el motor borra toda la lógica antigua y carga la nueva de forma segura sin riesgo de alterar el terreno ni arruinar la economía.

### 3.2. Gestión de Entidades, Variables y Memoria
Para evitar trabajar siempre con IDs numéricos (ej. `station 34`), la consola tendrá estado y memoria:
*   **String Lookup:** Las entidades admiten nombres (ej. `station "Central"`). El intérprete buscará la primera coincidencia exacta en el registro global.
*   **Variables:** La consola mantendrá una "Tabla de Símbolos". Ej: `$s1 = new station "Alpha"`. 
*   **Atajo de Inspección:** Si se teclea solo una variable (`$s1`) y Enter, actúa como `info $s1`, mostrando detalles del objeto (coordenadas, estado).
*   **Idempotencia (UPSERT):** Si un script hace `$s = new station "Alpha"` y la estación "Alpha" ya existe, el motor no la duplica ni falla; simplemente enlaza la entidad existente a `$s`.
*   **Garbage Collection (Punteros Huérfanos):** Si el jugador hace `del $s1`, el motor físico destruye la estación y **la consola elimina la variable `$s1` de su memoria**. Un uso posterior de `$s1` devolverá `Error: Variable not defined`.
*   **Persistencia:** La "Tabla de Símbolos" de la consola se guardará en el JSON del *savegame*. Al cargar partida, las variables seguirán funcionando.

### 3.3. Máquina de Estados del Cursor (Turtle Graphics)
El cursor actúa como un lápiz (estado persistente). Los comandos de avance (`forward`, `backward`) cambian según su modo activo:
1.  **`mode move`:** Navegación pura. No altera el mapa.
2.  **`mode write`:** Bajar el lápiz. Cualquier movimiento instanciará vías.
3.  **`mode del (borrar)`:** Goma de borrar. Destruye la vía por la que pasa (`backward 5`). No hacen falta instrucciones de giro porque sigue el trazado de la vía existente.
4.  **`mode clear` (triturador):** Deja la vía intacta, pero **elimina pieza a pieza** cualquier vehículo (locomotora o vagón individual) que pise el cursor. Si quieres borrar un tren entero de golpe usarías `del tr here`, pero `mode clear` te da precisión quirúrgica: te permite "comer" solo la locomotora dejando el resto de vagones intactos, o recorrer un tren entero para triturarlo paso a paso.

### 3.4. Atajos de Secuencia (El fin de `new rail`)
Al utilizar la máquina de modos, el comando `new rail` se vuelve redundante y pesado. Se introduce en ANTLR4 una regla de **Secuencia de Movimiento**.
Si el usuario teclea una cadena separada por comas empezando por un número o dirección (ej. `20, l, 3, r, r, 2`), el intérprete lo desglosa internamente en comandos atómicos (`forward 20`, `left`, `forward 3`...). 
Se pueden mezclar **distancias métricas con saltos semánticos** directamente en la secuencia. Ej: `20, l, gn fo, r, ge` (Avanza 20, izquierda, avanza hasta el próximo desvío, derecha, avanza hasta el final de la vía).

El parser aplicará además reglas físicas del motor: dos giros consecutivos (ej. `l, l`) inyectarán automáticamente un avance de 1 unidad de forma implícita (`l, 1, l`), ya que la geometría del juego no permite giros mayores a 45 grados sin separación.

El resultado dependerá del modo activo:
* En `mode write`, esa cadena construirá todo ese trazado de golpe.
* En `mode move`, esa cadena desplazará el cursor por ese recorrido.

### 3.5. Casos Límite y Resolución de Colisiones
*   **Dirección en los desvíos (Forks):** Si haces `go next fork` y luego `forward`, ¿por qué rama sale? Solución: Saldrá siempre por la **ruta que esté activa** en la aguja. El cursor 3D muestra una flecha direccional.
*   **Bucles infinitos en circuitos cerrados:** El comando `ge` (`go to end`) en un óvalo colgaría el juego. Solución: Historial de vías pisadas en la orden actual; si pisa la misma dos veces, se detiene.
*   **Vías muertas en búsquedas:** Lanzar `go next station` hacia un tope abortará donde termine la vía.
*   **Solapamiento Inocuo y Creación de Desvíos:** En `mode write`, construir sobre una vía que ya existe es inocuo (no consume dinero extra ni da error). De hecho, es la mecánica base para crear desvíos: navegas por una recta existente y ejecutas un giro (`r` o `l`). Por tanto, usar comandos como `gn fo` en `mode write` tiene todo el sentido: el cursor "repintará" la vía sin consecuencias hasta llegar al desvío.
*   **Choques construyendo:**  Si haces `20, l, 10` y choca en el paso 15, **NO hay dry-run ni rollback**. Construye hasta chocar, escupe error y se detiene (Filosofía UNIX).
*   **Borrado de vías ocupadas (`mode del (borrar)`):** Si borras una vía que tiene un tren, la operación se aborta. El jugador deberá usar `del wagon here` para destruir el obstáculo.

### 3.6. Integración con el Motor Económico
*   **Bancarrota / Deuda:** Construir cuesta dinero. Como el juego permite saldos negativos, un script enorme (`1000`) sin fondos **no se detendrá**. Hundirá la cuenta en una deuda millonaria.
*   **Reembolsos:** Los comandos de `del` devuelven el valor residual correspondiente.

### 3.7. Regeneración Procedural (Comando `new`)
*   Comandos como `new map mountains=high gold=low` inyectarán parámetros temporales en el sistema procedural, vaciando colecciones e invalidando cachés gráficas al vuelo.

### 3.8. Esquema Modal de Teclas (Hotkeys estilo vim)

Junto a la consola `:`, la interacción directa se organiza en **modos** con filosofía vim: cada modo se entra con una tecla y tiene sus propias acciones. Para que todos los conceptos tengan cabida, algunas teclas actuales se remapean según este esquema.

#### Modos y teclas de acceso

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
| Link | `l` |
| Unlink | `u` |
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

#### Link (`l`)

- `⏶,⏷`: Front/Back · `⏴,⏵`: Select/Unselect wagons · `o`: Locate · `Space`: Link

#### Unlink (`u`)

- `⏶,⏷`: Front/Back · `⏴,⏵`: Select/Unselect wagons · `o`: Locate · `Space`: Unlink

#### Program (`p`)

- Muestra el IDE de scripts.

#### Equivalencias con la consola `:`

Las teclas modales son abreviaturas directas de comandos de la consola. Mientras la CLI usa alias de dos letras (`fo`, `sn`, `st`...), el esquema modal usa una sola letra por tipo (fork, rail, sensor, semaphore, signal, station, locomotive):

| Modal | Consola `:` |
|---|---|
| `g f #` | `go fo #` |
| `g s #` | `go sn #` |
| `g m #` | `go sm #` |
| `g i #` | `go si #` |
| `g t #` | `go st #` |
| `g l #` | `go lo #` |
| `g[np] r` | `gn ra \| gp ra` |
| `g[np] f` | `gn fo \| gp fo` |
| `g " #` | `go "#` |
| `g e` | `go en` |
| `r [nswe]` | `face n\|s\|w\|e` |
| `f [fsmitl] #` | `face [fsmitl] #` |

### 3.9. Retroalimentación de Comandos (Eco)

- Si un comando no puede ejecutarse, se muestra un **error en la consola**.
- Si un comando puede ejecutarse pero no termina nunca (p. ej. `g e` en una vía que es un circuito cerrado), el usuario debe poder **cancelarlo con `Esc`**.
- El board muestra: el **modo actual** ("Writing", "Erasing", etc.), el **comando que se está tecleando** (p. ej. `g n ...` mientras espera a que se complete) y una **descripción** de lo que hará.
- `.` repite la última acción (comando punto).

---

## 4. Anexo: Diccionario de Comandos y Ejemplos

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

El esquema modal (sección 3.8) usa una sola letra por tipo: `f` (fork), `r` (rail), `s` (sensor), `m` (semaphore), `i` (signal), `t` (station), `l` (locomotive).

### Control de Trenes
* `train 1 start motor`: Arranca el motor.
* `train 1 set speed 3`: Establece la velocidad a 3.
* `train 1 stop`: Detiene el tren.
* `train 1 forward to contact`: Avanza a velocidad de maniobra hasta chocar y se detiene.
* `train 1 link forward all`: Ordena acoplar vagones.
* `train 1 invert`: Invierte la marcha del tren.

### Navegación de Cursor
* `go 232,34`: Salto a coordenadas absolutas.
* `go m-1`: Ir a la marca 1.
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
* `mode write`: Entra en modo construcción.
* `20, l, 3, r, 32, m-1, l, l, 32`: Construye toda la secuencia de golpe.
* `gn fo, r, ge`: Avanza al próximo desvío, gira a la derecha y avanza hasta el final de la vía.
* `step 10`: Avanza 10 unidades.
* `mode move`: Entra en modo espectador.
* `14, l, 2`: Se desplaza por la red sin construir.
* `mode del`: Entra en modo goma de borrar.
* `backward 5`: Borra 5 unidades hacia atrás.
* `mode clear`: Pasa el "triturador" (borra vehículos, deja la vía).
* `forward 20`: Triturará todos los vagones sueltos en los próximos 20 pasos.

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
* `mark 1`: Guarda la coordenada actual en `m-1`.
* `mark central_hub`: Guarda una marca de texto (usable luego con `go central_hub`).

---

## 5. Roadmap de Implementación (Fases MVP)

1.  **Fase 1 (Administración Directa):** Integrar ANTLR4 con Java. Solo comandos directos (`train 1 stop`, `save`, `new`).
2.  **Fase 2 (Navegación Topológica):** Crear el Cursor. Parsear secuencias de movimiento simples y comandos de navegación (`go 10,20`, `gn fo`, `g st 1`).
3.  **Fase 3 (Lápiz y Goma - Turtle Graphics):** Implementar la máquina de modos (`write`, `move`, `del`, `clear`). Modificar mapa mediante cadenas `20, l, 3`.
4.  **Fase 4 (Memoria y Variables):** Tabla de símbolos. Guardado JSON. *String Lookup* y resolución UPSERT. (`$s1 = new...`).
5.  **Fase 5 (Motor de Macros - Opcional):** Ejecución de scripts largos en ficheros externos (`:load script.letrain`).
6.  **Fase 6 (Hotkeys Modales):** Esquema modal de la sección 3.8: parser de prefijos `g` / `g[np]`, marcas `"`, modos Add/Vehicles/Drive, eco de comandos (3.9) y comando `.`.
