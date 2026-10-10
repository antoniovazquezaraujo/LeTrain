[🇬🇧 Read in English](cheatsheet.md)

# LeTrain - Chuleta Rápida de Comandos CLI

---

## 1. Navegación Topológica y Absoluta


| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `go <x>, <y>;` | Mueve el cursor a coordenadas cartesianas absolutas. | `go 10, -5;` |
| `go next <entidad>;` | Sigue la vía hacia adelante hasta encontrar la entidad. | `go next fork;` |
| `go <entidad> <id>;` | Teletransporta el cursor a la ubicación de una entidad. | `go st "Madrid";` |
| `go prev <entidad>;` | Sigue la vía hacia atrás hasta encontrar la entidad. | `go prev st;` |
| **`gn <entidad>;`** | Abreviatura rápida para `go next`. | `gn st;` |
| **`gp <entidad>;`** | Abreviatura rápida para `go prev`. | `gp fk;` |
| `go end;` | Sigue la vía actual hasta que se acabe el raíl. | `go end;` |

> **Abreviaturas de Entidades**: 
> - **`st`** = `station`
> - **`sn`** = `sensor` 
> - **`fk`** = `fork`
> - **`sm`** = `semaphore`
> - **`sg`** = `signal`
> - **`tr`** = `train`
> - **`rl`** = `rail`

> **Abreviaturas de Comandos**: 
> - **`go`** = `g`
---

## 2. Marcadores (Bookmarks)


| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `mark <nombre>;` | Guarda la coordenada actual bajo un nombre o número. | `mark base;` |
| `go mark <nombre>;` | Teletransporta el cursor a la marca. | `go mark base;` |
| **`g m <nombre>;`** | Abreviatura rápida para ir a una marca. | `g m base;` |
> **Nombres de marcas**: 
- Si el nombre de una marca incluye espacios debe ir entre comillas, p.e. `go mark "estación central"`
---

## 3. Orientación del Cursor


| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `face <dirección>;` | Gira a un punto cardinal (`n, s, e, w, ne, nw, se, sw`). | `face ne;` |
| `face <entidad> <id>;` | Rota el cursor para apuntar en dirección a una entidad. | `face tr 1;` |
| `face m <string>;` | Rota el cursor para apuntar en dirección a una marca. | `face m "madrid";` |

---

## 4. Gestión de Vías (Turtle Graphics)


| Comando Base | Acción | Ejemplo |
| :--- | :--- | :--- |
| `write <secuencia>;` | Avanza creando vía nueva. | `write 5, l, m base, r, 1;` |
| `move <secuencia>;` | Avanza el cursor sin crear nada. | `move 5, l;` |
| `del <secuencia>;` | Avanza **arrancando la vía** (destruye trenes y entidades). | `del 3;` |
| `clear <secuencia>;` | Avanza borrando **solo trenes y vagones** (respeta vías). | `clear 10;` |

> **Elementos de secuencias**: 
- `<número>`: Casillas a avanzar recto.
- **`l`**: Girar a la izquierda (left).
- **`r`**: Girar a la derecha (right).
- `m <nombre_marca>`: Navegar automáticamente hasta la marca.

Si no se incluye una distancia después de un giro, se avanza por defecto un paso, p.e. la secuencia "r,r,3" equivale a "r,1,r,3"

---
## 5. Creación y Borrado de Entidades Fijas y Vehículos


| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `new st;` / `new sm;` / `new sg;` | Crea una infraestructura en la posición del cursor. | `new st;` |
| `new loco <Letra> [color];` | Crea una locomotora. Letra y color  | `new loco Z blue;` |
| `new wagon <Letra> [tipo];` | Crea un vagón. Letra y tipo:  | `new wagon B ruby;` |
| `del <entidad> <id>;` | Borra una entidad (estaciones, semáforos, etc) dejando la vía intacta. | `del sm 1;` |
| `clear <entidad> <id>;` | Borra un tren o vagón por su ID. | `clear tr 1;` |

---

> **Colores disponibles**:
> `red`, `green`, `blue`, `yellow`, `black`, `white`, `orange`, `purple`, `gray`, `brown`
>
> **Tipos de mercancía**:
> `coal`, `gold`, `ruby`

---

## 6. Control Directo de Infraestructura


| Entidad | Comando | Ejemplo |
| :--- | :--- | :--- |
| **Fork** (Desvíos) | `fork <id> set straight;` / `set curved;` / `set <dirección>;` / `flip;` | `fork 1 set curved;` |
| **Semaphore** (Semáforos) | `semaphore <id> open;` / `close;` / `toggle;` / `invert;` | `semaphore 2 toggle;` |
| **Speed Signal** (Velocidad) | `signal <id> set limit <número>;` <br> `signal <id> set mode max;` / `set mode min;` <br> `signal <id> invert;` | `signal 3 set limit 120;` <br> `signal 3 set mode max;` |
| **Station** (Estaciones) | `station <id> invert;` | `station 1 invert;` |
| **Sensor** | `sensor <id> invert;` | `sensor 2 invert;` |

---

## 7. Control Directo de Trenes y Vehículos


| Comando Base | Acción | Ejemplo |
| :--- | :--- | :--- |
| `train <id> couple <dir> [n\|all];` | Engancha vagones en la dirección indicada (`forward`/`fw` o `backward`/`bw`). Si no se indica `n`, engancha todos; `all` es explícito. | `train 1 couple forward 2;` o `train 1 couple backward all;` |
| `train <id> uncouple <dir> [n\|all];` | Desengancha vagones en la dirección indicada (`forward`/`fw` o `backward`/`bw`); sin número desengancha todos los de ese lado (igual que `couple`), `all` es explícito. | `train 1 uncouple backward 1;` |
| `train <id> set speed <n>;` | Asigna velocidad. | `train 1 set speed 5;` |
| `train <id> stop at station <id\|"nombre"> [speed <n>];` | Va a la estación y para en ella; la velocidad va en la orden. | `train 1 stop at station "B" speed 3;` |
| `train <id> stop at sensor <id\|"nombre"> [speed <n>];` | Va al sensor y para encima de él. | `train 1 stop at sensor 5 speed 2;` |
| `train <id> stop at end [speed <n>];` | Va al fin de vía y frena en la última vía. | `train 1 stop at end speed 2;` |
| `train <id> stop when blocked [speed <n>];` | Avanza y para en el primer bloqueo (no reanuda). | `train 1 stop when blocked speed 2;` |
| `train <id> stop on contact [speed <n>];` | Avanza hasta tocar al vehículo de delante y queda pegado (listo para `couple`); a velocidad de choque el toque es choque. | `train 1 stop on contact speed 2;` |
| `train <id> reverse;` | Invierte la dirección de la marcha (`invert` es sinónimo). | `train 1 reverse;` |
| `train <id> stop;` | Frena y desactiva el autopilot. | `train 1 stop;` |
| `train <id> park;` | Frena, apaga el motor y mantiene el autopilot. | `train 1 park;` |
| `train <id> set engine on;` / `off;` | Enciende o apaga el motor de la locomotora . | `train 1 set engine on;` |
| `train <id> set autopilot true;` | Activa el autopiloto. | `train 1 set autopilot true;` |
| `train <id> load;` / `unload;` | Carga o descarga mercancías (requiere estar en estación). | `train 1 load;` |

> `couple`/`uncouple` funcionan por el **sentido físico** del tren: `forward` es el lado de la
> cabeza y `backward` la cola. Con la locomotora en cabeza tirando de los vagones, los vagones van
> detrás: usa `uncouple backward 1` para dejarlos (las mismas órdenes valen como acciones de
> waypoint).
>
> `all` es palabra reservada (`uncouple backward all`); para usarla como nombre hay que
> entrecomillarla: `info station "all";`.

---

## 8. Guardado y Carga

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `save <nombre?>;` | Guarda el mapa. Si omites el nombre, usa uno automático. | `save "mapa1";` |
| `load <nombre?>;` | Carga el mapa. | `load "mapa1";` |

---


## 9. Nombres y Referencias

Cualquier tren o infraestructura (estaciones, semáforos, marcas, etc.) puede recibir un nombre para que no tengas que recordar su ID numérico.

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `<entidad> <id> set name "<nombre>";` | Asigna un nombre a una entidad usando su ID numérico. | `train 1 set name "Expreso";`<br>`st 2 set name "Central";` |
| `<entidad> "<nombre_viejo>" set name "<nombre_nuevo>";` | Cambia el nombre a una entidad que ya tiene uno. | `train "Expreso" set name "Ave";` |

Una vez nombrada, **puedes usar el nombre entre comillas** (o sin ellas si no tiene espacios) en cualquier comando que pida un `<id>`:

- `train "Ave" set engine on;`
- `go st "Central";`
- `clear tr "Ave";`

> **Mayúsculas estrictas**: las keywords van siempre en minúsculas (`train`, `station`, `sensor`…) y los nombres se buscan **exactos**, tal como se escribieron: `"Central"` y `"central"` son nombres distintos. Una referencia con la caja equivocada avisa `not found` y no hace nada. Dentro de `program { ... }` el texto se ejecuta tal cual, strings incluidos: no se pasa nada a minúsculas.

---

## 10. Reloj de Juego

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `time;` | Muestra el día y la hora actuales del juego. | `time;` |
| `time set <hora>;` | Fija la hora del día actual (`00`–`23`); los minutos vuelven a `:00`. | `time set 9;` |
| `time set <HH:MM>;` | Fija hora y minuto (`00:00`–`23:59`); se aceptan horas de un dígito. | `time set 18:45;` |

> Un cambio correcto es silencioso: mira el reloj del HUD. Un valor fuera de rango (`time set 25;`, `time set 25:99;`) avisa `Invalid time …` y deja el reloj intacto.

---

## 11. Disparadores Temporales (`at` / `every`)

Bloques que se ejecutan por el **reloj de juego** (el mismo bloque de acciones que los triggers de
eventos: terminado en `;`, sin anidar). El primer disparo es siempre la siguiente ocurrencia
**estrictamente posterior** al registro.

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `at <HH[:MM]> { ... }` | Ejecuta el bloque cada día a esa hora de juego. | `at 6:30 { semaphore 1 open; }` |
| `every <n>m { ... }` | Cada `n` minutos de juego, en la rejilla anclada a 00:00. | `every 30m { semaphore 1 open; }` |
| `every <n>h { ... }` | Cada `n` horas de juego, en punto. | `every 2h { semaphore 1 close; }` |
| `every <n>d { ... }` | Cada `n` días de juego a las 00:00 (o a la hora de `from`). | `every 1d { semaphore 1 close; }` |
| `every <n>m from <HH[:MM]> { ... }` | Ancla la rejilla a `from` en vez de a 00:00. | `every 30m from 6:15 { semaphore 1 open; }` |
| `train <id> <acción>;` | Acción de tren dentro del bloque (referencia explícita obligatoria). | `train 5 set engine on;` |
| `train at station <id\|"nombre"> <acción>;` | Apunta al tren que esté en el sitio cuando el bloque se dispara. | `train at station "Mina" load;` |

> Una acción de tren genérica y sin referencia (`train set speed 5;`) **no está permitida** en un
> disparador temporal: avisa de forma visible cuando el bloque se dispara y se ignora. Máximo 64
> disparadores activos (los duplicados avisan). Al cargar una partida no se recuperan las horas
> perdidas: un `at` ya pasado espera al día siguiente y `every` continúa en su siguiente punto de
> rejilla. La pausa de edición congela el reloj, así que los disparos no se acumulan. Sintaxis
> completa en **[grammar_es.md](grammar_es.md)** §4.

---

## 12. Información de la App

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `version;` | Muestra la versión del build en ejecución (la misma que el título de la ventana). | `version;` |
