# LeTrain - Gramática de Scripting (Automation)

LeTrain incluye su propio analizador léxico/sintáctico (basado en ANTLR4) que te permite automatizar la red ferroviaria usando un lenguaje específico. Los scripts se ejecutan línea a línea. El mismo lenguaje se usa desde la consola (comandos directos) y dentro de la sección `program { ... }` de un fichero de escenario (ver **[scenarios_es.md](scenarios_es.md)**).

## ⚙️ Estructura del Lenguaje

El lenguaje admite tres tipos principales de sentencias: comandos directos, creación/asignación de itinerarios (Autopilot) y bloques disparados por eventos (*triggers*).

### 1. Comandos Directos
Se ejecutan inmediatamente. **Requieren punto y coma (`;`) al final**.

**Acciones de Trenes (`trainRef` puede ser número o nombre entre comillas):**
- `train [ID] accelerate;`
- `train [ID] decelerate;`
- `train [ID] set speed [NUM];` o `train [ID] set [NUM];`
- `train [ID] invert;`
- `train [ID] stop at station [ID|"nombre"] [speed NUM];`
- `train [ID] stop at sensor [ID|"nombre"] [speed NUM];`
- `train [ID] stop at end [speed NUM];`
- `train [ID] stop when blocked [speed NUM];`
- `train [ID] set engine on;` / `train [ID] set engine off;`
- `train [ID] set forward;` / `train [ID] set backward;`
- `train [ID] load;`
- `train [ID] unload;`
- `train [ID] couple forward [NUM|all];` / `train [ID] uncouple backward [NUM|all];`
  (`all` engancha o desengancha todos los vehículos de ese lado; `uncouple backward all` deja la
  locomotora sola y los vagones en su propio tren)`

**Palabra reservada `all`**: `all` es el contador de `couple`/`uncouple` (`uncouple backward all`), así
que un nombre desnudo `all` en otra orden es un error de sintaxis (`info station all;`); entrecomíllalo
para usarlo como nombre: `info station "all";`.

**Misiones de un solo uso (`stop at …`)**: el destino y la velocidad viajan en la misma orden, así
el tren no arranca antes de recibir el destino. La velocidad se aplica al empezar la maniobra (sin
`speed`, o con `speed 0`, se usa la que el tren tenga puesta; si es 0 la orden se rechaza con aviso)
y el tren acaba siempre parado. El destino puede ser una estación, un sensor, el fin de vía **por
delante del tren** (frena en la última vía, sin tocar el tope; un bucle cerrado sin final por delante
avisa y no mueve) o el primer bloqueo (`stop when blocked` rueda con la curva del #633 hasta la
última vía de su cantón ante el bloqueo y completa parado ahí; **no** reanuda al liberarse y, si el
bloqueo se libera antes de parar, sigue). Si el destino solo es alcanzable en sentido contrario, la
orden invierte el
tren una vez al empezar. Una orden recibida mientras el tren cumple un itinerario se rechaza con
aviso (no se pausa nada): usa `train N set autopilot false;` o escribe la maniobra en el itinerario.
Si el destino se vuelve inalcanzable a mitad de misión (se pierde la ruta) o el tren se queda parado
sin espera de bloque/horario/carga durante aproximadamente una hora de juego, la misión falla con
aviso. Las señales y los cantones siguen mandando: la curva de frenado de la misión solo baja la
velocidad, nunca la sube. Una orden nueva reemplaza a la misión en curso.

**Nombrar Elementos:**
- `station [ID] set name "Mi Estacion";`
- `sensor [ID] set name "Sensor Norte";`
- `train [ID] set name "Mercancias";`

### 2. Autopilot e Itinerarios
Permite programar una lista de destinos (waypoints) para que el tren busque el camino mediante A*.
Los bloques de itinerario usan llaves `{ }`; el `;` al final de un waypoint es opcional y la `}` de
cierre termina el bloque (el `;` detrás de ella también es opcional; la consola lo añade solo al
teclear el bloque). Un waypoint puede llevar además un horario: `arrival HH:MM` y/o
`departure HH:MM` (reloj de 24 h, `H:MM` o `HH:MM`).

**Crear Itinerario:**
```letrain
create itinerary "RutaCarbon" {
    add station 1 load
    add station 2 arrival 10:23, reverse, unload, departure 10:30
    add sensor 5 speed 20
}
```
*Reglas de los waypoints:*

- **Al menos dos waypoints**: un itinerario es un bucle, así que un plan con un único waypoint se rechaza con aviso y no se asigna (el autopilot queda apagado). Para un único destino usa una orden suelta `stop at …` (o una acción de waypoint dentro de un servicio); repetir la misma estación está permitido.
- Las **comas son obligatorias** entre las acciones del waypoint. La referencia a la estación/sensor y la dirección de entrada opcional (una dirección de brújula como `n`, `e`, `s`, `w`) **no** llevan coma.
- El **orden es obligatorio**: `arrival` primero, después las acciones en su orden de ejecución (`load`, `unload`, `reverse`, `stop`, `park`, `wait [NUM]`, `speed [NUM]`, `uncouple forward|backward [NUM|all]`, `couple forward|backward [NUM|all]`, `stop at station|sensor [REF] [speed NUM]`, `stop at end [speed NUM]`, `stop when blocked [speed NUM]`, `fork [ID] set straight|curved`, `fork [ID] flip`) y `departure` al final. Escribir un atributo fuera de orden es un error de sintaxis.
- **Maniobras**: las órdenes de movimiento (`stop at …`, `stop when blocked …`) son **misiones** que se ejecutan al llegar al waypoint y deben completarse antes de la siguiente acción: el tren conduce y acaba parado. `stop at` usa el sentido actual y **no auto-invierte**: escribe el `reverse` que necesites o la orden se rechaza con un aviso de "sin ruta desde el sentido actual". Una maniobra rechazada **aborta las acciones restantes de ese waypoint** (el `departure` y la ruta al siguiente waypoint siguen), para que la coreografía no continúe en un estado raro. Las acciones de fork fuerzan o preparan una aguja; el autopilot sigue orientando las agujas a lo largo de la ruta que calcula. El `departure` libera cuando la maniobra ha terminado (si acaba tarde, el tren sale tarde y se mide el desfase); después, la ruta al siguiente waypoint se recalcula desde donde haya quedado el tren.
- **Maniobras y cantones**: una maniobra cuyo destino está dentro de un cantón ocupado por **su propia parte desenganchada** (enganchar los vagones que acaba de dejar) puede entrar en ese cantón como una maniobra manual; las comprobaciones físicas siguen parando el tren antes de cualquier vehículo. Un cantón ocupado por un tren ajeno mantiene el bloqueo: la maniobra espera en la frontera y reanuda al liberarse.
- **Dirección de `uncouple`**: `uncouple forward` desengancha por el **lado de la cabeza** y `uncouple backward` por la cola. Con la locomotora en cabeza tirando de los vagones, los vagones van detrás: el run-around se escribe `uncouple backward 1`.
- `arrival` se mide al llegar al waypoint; `departure` es la hora programada de salida: al llegar se ejecutan las acciones, el tren espera hasta ella y la salida programada arranca el motor. La estancia es `departure − arrival` en tiempo de juego. Las horas se leen en secuencia: una hora menor que la anterior pertenece al día siguiente (`arrival 23:50, departure 00:10`). Si el tren llega tarde, sale de inmediato y se mide el desfase. Sin horas, el waypoint se comporta exactamente como antes.
- `park` frena, apaga el motor y **mantiene el autopilot activo** (a diferencia de `stop`, que frena y desactiva el autopilot). La siguiente salida programada arranca el motor y recupera la velocidad de crucero, de modo que un servicio diario que se repite puede terminar con `park` y volver a salir a la mañana siguiente. Un `park` **sin salida programada posterior** deja el tren aparcado (motor apagado, plan conservado): no vuelve a moverse hasta que una salida programada lo arranque o lo conduzcas manualmente.
- El tren muestra su puntualidad en `info train N`: desfases de llegada y salida por parada en minutos de juego (`+` = tarde, `−` = adelantado), más el desfase actual, medio y máximo. Un tren sin horas no muestra nada.
- La seguridad manda: la retención nunca pisa los cantones; si el bloque siguiente está ocupado, el tren espera y el retraso aparece en la siguiente medida.
- Las horas se validan, se guardan y se exportan.
- La sintaxis vieja sin comas (p. ej. `add station 2 reverse unload`) se **rechaza en todos los puntos de entrada** (juego, editor y `letrain-check`) con un diagnóstico: esta beta no migra los itinerarios antiguos.

**Asignar y Activar:**
- `assign itinerary "RutaCarbon" to train 1;`
- `train 1 set autopilot true;`

### 3. Automatización por Eventos (Triggers)
Responde a eventos del juego en tiempo real. 

**Estructura Base:**
```letrain
[SELECTOR] on [EVENTO] {
    [ACCION];
    [ACCION];
}
```

**Selectores:**
- `sensor [ID]`, `fork [ID]`, `semaphore [ID]`, `station [ID]`, `train [ID]` (o `train` genérico).

**Eventos:**
- Trenes: `on train enter`, `on train exit`, `on train couple`, `on train uncouple` (opcionalmente con dirección `forward`/`backward`).
- Accidentes: `train 1 on crash`, `train on contact forward`.

**Acciones especiales dentro de bloques (terminan en `;`):**
- *Semáforos:* `semaphore [ID] set open;` / `semaphore [ID] set closed;`
- *Cambios de Aguja (Forks):* `fork [ID] set flip;` / `fork [ID] set straight;` / `fork [ID] set curved;`
- *Tren Condicional:* Puedes usar `train at station [ID]`, `train at sensor [ID]`, `train at fork [ID]`, o `train at semaphore [ID]` en lugar de usar un número fijo de tren para aplicar acciones al tren que disparó el evento o que se encuentre allí.

### 4. Comandos del Juego y Editor (Consola)
Puedes teclear estos comandos directamente en el CLI para gestionar el estado del juego, el cursor y los archivos.

**Estado del Juego y Archivos:**
- `save [archivo];` / `load [archivo];` - Guardar o cargar un mapa (partida guardada: el *estado* actual). Si el nombre es una palabra reservada (p. ej. `speed`, `train`, `station`), ponlo entre comillas: `save "speed";`.
- `export [archivo];` / `import [archivo];` - Exportar o importar un escenario (una *receta* `.ltr`; ver **[scenarios_es.md](scenarios_es.md)**).
- `quit;` o `q` - Salir del juego.

**Información y Ayuda:**
- `help;` - Muestra todos los grupos de comandos (`CONSOLE`, `BUILD`, `PROGRAM`, `CONFIG`). Fíltralo con `help console;`, `help build;`, `help program;`, `help config;`, o un tema suelto como `help ls;`.
- `ls;` - Lista todas las entidades. `ls [tipoEntidad];` lista un tipo (ej., `ls station;`).
- `info;` - Muestra una vista general de todo el mundo. `info [tipoEntidad];` lista ese tipo; `info [tipoEntidad] [ID|nombre];` detalla una entidad concreta.

**Borrado:**
- `del [tipoEntidad] [ID];` - Borrar una infraestructura específica (ej., `del station 1;`). *Nota: No sirve para trenes.*
- `clear train [ID];` - Borrar un tren específico del mapa (ej., `clear train 1;`). *Nota: CLEAR es exclusivo para vehículos.*

**Movimiento del Cursor y Marcas:**
- `go [NUM], [NUM];` - Mover el cursor a una coordenada X, Y absoluta.
- `go [tipoEntidad] [ID];` - Saltar con el cursor a una entidad (ej., `go station 1;`).
- `go next [tipoEntidad];` / `go prev [tipoEntidad];` - Ciclar el cursor por las entidades.
- `mark [ID];` o `m [ID];` - Guardar la posición actual del cursor en una marca.
- `go mark [ID];` o `go m [ID];` - Saltar con el cursor a una marca guardada.
- `face [DIR];` - Girar el cursor para mirar a una dirección (`dir_n`, `dir_s`, `dir_e`, `dir_w`, etc.).

**Acciones de Infraestructura (Directas):**
Puedes dar comandos directos a la infraestructura fuera de los bloques de eventos:
- `semaphore [ID] set open;` / `semaphore [ID] set closed;` / `semaphore [ID] invert;`
- `fork [ID] set straight;` / `fork [ID] set curved;` / `fork [ID] flip;`
- `signal [ID] set limit [NUM];` / `signal [ID] set mode (max|min);` / `signal [ID] invert;`
- `station [ID] invert;` - Invertir la orientación de una estación para que mire en sentido contrario a lo largo de la vía (el andén cambia de lado, igual que Espacio en modo STATIONS).
- `sensor [ID] invert;` - Invertir la dirección de detección de un sensor plano (igual que Espacio en modo SENSORS).

**Mover Elementos de Vía (slide):**
Desplaza una estación/sensor/semáforo/señal de velocidad una o más celdas de reposo a lo largo de la vía, en la dirección a la que mira el propio elemento. Equivalente a Shift+Flecha en los modos de edición.
- `slide station [ID] fw [N];` / `slide station [ID] bw [N];` - Mover una estación hacia delante/atrás.
- `slide sensor [ID] fw [N];` / `slide sensor [ID] bw [N];` - Mover un sensor.
- `slide semaphore [ID] fw [N];` / `slide semaphore [ID] bw [N];` - Mover un semáforo.
- `slide signal [ID] fw [N];` / `slide signal [ID] bw [N];` - Mover una señal de velocidad.

Por defecto mueve una celda hacia delante. Si el elemento está bloqueado o al final de la vía, el comando devuelve un error.

**Diario de Edición, Undo y Redo (Durante grabación):**
Mientras la **grabación** está activa, cada edición se graba en el diario de comandos, de modo que se puede deshacer y rehacer de forma determinista. La grabación se conmuta con la tecla **`R`**.
- `journal;` - Muestra el estado de grabación y la lista de comandos grabados (en orden) — lo que reproduciría una exportación.
- `undo;` / `undo [N];` - Deshace las últimas N ediciones (1 por defecto). La tecla **`u`** hace lo mismo.
- `redo;` / `redo [N];` - Rehace las últimas N ediciones deshechas. **Ctrl+R** hace lo mismo.

**Modo Tortuga (Construcción por Script):**
Puedes usar `write`, `move`, `del`, o `clear` para hacer secuencias de movimientos con el cursor y automatizar la construcción de vías.
- `write 5, r, 5, l, 10;` - Dibujar vías: avanza 5, gira derecha, avanza 5, gira izquierda, avanza 10.

---

### Ejemplo Completo

```letrain
// Nombramos la estación
station 1 set name "Mina Central";

// Creamos la ruta del tren
create itinerary "RutaPrincipal" {
    add station 1 load
    add station 2 unload
}

// Activamos la ruta
assign itinerary "RutaPrincipal" to train 1;
train 1 set autopilot true;

// Automatizamos el cruce para cualquier tren que pise el sensor
sensor 4 on train enter {
    fork 2 set straight;
    semaphore 1 set open;
}
```

---
<div align="center" style="margin-top: 40px; margin-bottom: 40px;">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
  <img src="https://img.shields.io/badge/LibGDX-Engine-E3363E?style=flat-square&logo=libgdx&logoColor=white" alt="LibGDX">
  <img src="https://img.shields.io/badge/Open_Source-%E2%9D%A4%EF%B8%8F-2EA44F?style=flat-square" alt="Open Source">
  <br><br>
  <strong>The Letter Train Simulator (LeTrain)</strong><br>
  Desarrollado con ☕ por <a href="https://github.com/antoniovazquezaraujo">Antonio Vázquez Araújo</a><br><br>
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/issues">Reportar un Bug</a> &nbsp;|&nbsp; 
  <a href="https://github.com/antoniovazquezaraujo/LeTrain">Código Fuente</a> &nbsp;|&nbsp; 
  <a href="mailto:antoniovazquezaraujo@gmail.com">Contacto (Email)</a>
</div>
