# LeTrain CLI Cheat Sheet

Esta es la lista completa de comandos y abreviaturas que puedes usar en la consola del juego (pulsando `:`). Recuerda que **todos los comandos deben terminar con punto y coma (`;`)**.

---

## 1. Navegación Topológica y Absoluta

Puedes mover el cursor por el mapa de diferentes formas. La palabra clave `go` se puede abreviar como **`g`**.

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `go <x>, <y>;` | Mueve el cursor a coordenadas cartesianas absolutas. | `go 10, -5;` |
| `go <entidad> <id>;` | Teletransporta el cursor a la ubicación de una entidad. | `g st "Madrid";` o `g sn 1;` |
| `go next <entidad>;` | Sigue la vía hacia adelante hasta encontrar la entidad. | `go next fork;` |
| `go prev <entidad>;` | Sigue la vía hacia atrás hasta encontrar la entidad. | `go prev st;` |
| **`gn <entidad>;`** | Abreviatura rápida para `go next`. | `gn st;` |
| **`gp <entidad>;`** | Abreviatura rápida para `go prev`. | `gp fk;` |
| `go end;` | Sigue la vía actual hasta que se acabe el raíl. | `g end;` |

> **Abreviaturas de Entidades**: 
> - **`st`** = `station`
> - **`sn`** = `sensor` 
> - **`fk`** = `fork`
> - **`sm`** = `semaphore`
> - **`sg`** = `signal`
> - **`tr`** = `train`
> - **`rl`** = `rail`

---

## 2. Marcadores (Bookmarks)

Muy útil para guardar posiciones del cursor a las que vas a volver frecuentemente.

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `mark <nombre>;` | Guarda la coordenada actual bajo un nombre o número. | `mark "base";` |
| `go mark <nombre>;` | Teletransporta el cursor a la marca. | `go mark "base";` |
| **`g m <nombre>;`** | Abreviatura rápida para ir a una marca. | `g m "base";` |
---

## 3. Orientación del Cursor

Permite girar el cursor sin moverlo de su casilla.

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `face <dirección>;` | Gira a un punto cardinal (`n, s, e, w, ne, nw, se, sw`). | `face ne;` |
| `face <entidad> <id>;` | Rota el cursor para apuntar en dirección a una entidad. | `face tr 1;` |

---

## 4. Gestión de Vías (Turtle Graphics)

Estos comandos permiten pintar, borrar o moverse varias casillas seguidas, indicando los pasos a dar separados por comas. Las direcciones relativas (izquierda/derecha) avanzan automáticamente 1 casilla después de girar.

| Comando Base | Acción | Ejemplo |
| :--- | :--- | :--- |
| `write <secuencia>;` | Avanza creando vía nueva. | `write 5, l, m "base", r, 1;` |
| `move <secuencia>;` | Avanza el cursor sin crear nada. | `move 5, l;` |
| `del <secuencia>;` | Avanza **arrancando la vía** (destruye trenes y entidades). | `del 3;` |
| `clear <secuencia>;` | Avanza borrando **solo trenes y vagones** (respeta vías). | `clear 10;` |

- `<número>`: Casillas a avanzar recto.
- **`l`**: Girar a la izquierda (left).
- **`r`**: Girar a la derecha (right).
- `<nombre_marca>`: Navegar automáticamente hasta la marca.

---
## 5. Creación y Borrado de Entidades Fijas y Vehículos

Coloca el cursor en el lugar deseado y ejecuta:

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `new st;` / `new sm;` / `new sg;` | Crea una infraestructura en la posición del cursor. | `new st;` |
| `new loco <Letra> [color];` | Crea una Locomotora. Letra y color: `red, green, blue, yellow, black, white, orange, purple, gray, brown`. | `new loco Z blue;` |
| `new wagon <Letra> [tipo];` | Crea un Vagón. Letra y tipo: `coal, gold, ruby`. | `new wagon B ruby;` |
| `del <entidad> <id>;` | Borra una entidad (estaciones, semáforos, etc) dejando la vía intacta. | `del sm 1;` |
| `clear <entidad> <id>;` | Borra un tren o vagón por su ID. | `clear tr 1;` |

--

## 6. Control Directo de Infraestructura

Ya no necesitas hacer scripts para interactuar con semáforos y desvíos, puedes controlarlos directamente en la consola.

| Entidad | Comando | Ejemplo |
| :--- | :--- | :--- |
| **Fork** (Desvíos) | `fork <id> set left;` / `set right;` / `flip;` | `fork 1 flip;` |
| **Semaphore** (Semáforos) | `semaphore <id> open;` / `close;` / `invert;` | `semaphore 2 invert;` |
| **Speed Signal** (Velocidad) | `signal <id> limit <número>;` <br> `signal <id> set mode max;` / `set mode min;` <br> `signal <id> invert;` | `signal 3 limit 120;` <br> `signal 3 set mode max;` |

---

## 7. Control Directo de Trenes y Vehículos

Puedes enviar órdenes directas a un tren específico en lugar de hacerlo solo por eventos.

| Comando Base | Acción | Ejemplo |
| :--- | :--- | :--- |
| `train <id> couple <dir> [n];` | Engancha vagones en la dirección indicada (`forward`/`fw` o `backward`/`bw`). Si no se indica `n`, engancha todos. | `train 1 couple forward 2;` o `train 1 couple backward;` |
| `train <id> uncouple <dir> [n];` | Desengancha vagones en la dirección indicada (`forward`/`fw` o `backward`/`bw`). | `train 1 uncouple fw 1;` |
| `train <id> set speed <n>;` | Asigna velocidad. | `train 1 set speed 5;` |
| `train <id> reverse;` | Invierte la dirección de la marcha. | `train 1 reverse;` |
| `train <id> stop;` | Frena completamente (equivalente a `set speed 0`). | `train 1 stop;` |
| `train <id> set autopilot true;` | Activa el autopiloto. | `train 1 set autopilot true;` |
| `train <id> load;` / `unload;` | Carga o descarga mercancías (requiere estar en estación). | `train 1 load;` |

---

## 8. Guardado y Carga

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `save <nombre?>;` | Guarda el mapa. Si omites el nombre, usa uno automático. | `save "mapa1";` |
| `load <nombre?>;` | Carga el mapa. | `load "mapa1";` |

---

## 9. Comandos Completos de Scripting (Eventos y Rutas)

- **Asignación de nombres:** `st 1 set name "Madrid";`
- **Creación de rutas:** `create itinerary "Ruta1" { add st 1 add sn 2 }`
- **Asignación de rutas:** `assign itinerary "Ruta1" to train 1;`
- **Eventos Avanzados:** `sn 1 on train 1 enter { train 1 stop; fork 1 set right; semaphore 1 open; }`
