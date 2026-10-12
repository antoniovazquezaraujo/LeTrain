# LeTrain - Manual de Usuario

Bienvenido al manual oficial de **LeTrain**. Aquí encontrarás todo lo que necesitas saber para operar tu red ferroviaria, gestionar la economía y automatizar tus trenes, ya sea en el cliente clásico de terminal 2D o en la moderna vista 3D.

## 🎥 Videotutorial / Gameplay

<iframe width="1120" height="630" src="https://www.youtube.com/embed/VwS9Gbu3ygw" title="LeTrain Trailer" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>



---

## 🎮 Controles Básicos

El juego se puede controlar tanto en su versión 2D como en la 3D mediante el teclado. A continuación, las teclas y modos principales:

### Portada, menú File y Nueva Partida
- El juego abre con una **portada** sencilla (un tren dibujado en ASCII en la terminal 2D, texto en la vista 3D). Pulsa **cualquier tecla** para pasar al **menú File**: el juego 2D ya no arranca el mundo solo, así que **New** o **Load** son lo que inicia tu red ferroviaria.
- Pulsa **Esc** para abrir el menú **File**, con las mismas palabras en 2D y 3D: **New**, **Load**, **Save**, **Settings** y **Quit**. **Esc** de nuevo cierra el menú y **Quit** sale del juego.
  - **New** (2D) abre el diálogo de **Nueva Partida**: semilla, proporciones de terreno y las secciones del catálogo (ver abajo). Es la forma de empezar una partida desde el menú inicial. Con una partida en curso pide confirmación primero (el flujo completo Guardar/Descartar/Cancelar llegará más adelante); la partida sin guardar se descarta.
  - **Save** aparece deshabilitado con el motivo *no game in progress* hasta que haya partida.
  - **Settings** sigue visible pero deshabilitado hasta su rebanada; en el cliente 3D **New** y **Settings** están deshabilitados con *3D parity slice* por ahora.
- El **LeTrain Editor** ya no tiene botones `Save`/`Load`: viven en el menú File. Los comandos de consola `save;`/`load;` siguen funcionando.
- El comando de consola **`new game;`** abre el mismo diálogo de Nueva Partida desde la CLI.

#### Diálogo de Nueva Partida (2D)
- La pestaña **Terrain** se abre primero y concentra todos los controles del terreno: la semilla, los sliders de **Agua %**/**Montaña %**, la **barra apilada** (tan ancha como la fila del slider) y el **preview**. Las demás pestañas (**Game**, **Economy**, **Build**, **UI**) muestran solo el catálogo de ajustes.
- **Semilla**: al abrir el diálogo se sortea una semilla aleatoria. Sigue siendo editable y copiable.
- **Terreno**: la regla es *agua + montaña ≤ 95 %*: al mover un slider se ajusta el otro, de modo que siempre queda al menos un 5 % de campo. La **barra apilada** muestra el reparto del 100 % (agua / campo / montaña).
- **Preview**: muestreo adaptativo **cuadrado** del terreno (mismo ruido y umbrales calibrados que el generador del mundo) dibujado con medios bloques, de modo que el mundo conserva sus celdas 1:1; la montaña va en el marrón del juego. Va pegado al borde izquierdo y aparece a partir de **80×24**, creciendo con la terminal (16×16 celdas en 80×24, 22×22 en 120×30: en torno a un cuarto del área de la sección, para no comerse el diálogo); por debajo de 80×24 se mantienen los sliders y la barra.
- **Secciones**: **una columna con scroll** por pestaña, un campo por fila como `Nombre: valor` (más su unidad; casillas para las opciones de sí/no). Cada página alinea sus valores tras su propio nombre más ancho (en Terrain quedan pegados a sus etiquetas), así que el hueco de la izquierda desaparece. Al enfocar un campo queda armado: escribir sustituye el valor entero (mover el cursor lo cancela, Backspace lo borra). Sin pistas de rango en las filas: al confirmar un valor inválido (**Enter**/Tab) se conserva el anterior y se abre una cajita con el rango permitido (`Allowed: 0..95`, `Allowed: 00:00..23:59`, `Minimum: 0`, `Maximum: 12`). **Start** construye el mundo con esos valores; **Cancel**/Esc lo deja todo como estaba.
- **Pestaña Game**: además de los ajustes del mundo fija el origen del reloj de la nueva partida: **Start day** (1..366, por defecto 1) y **Start time** (`HH:MM`, por defecto 08:00; se acepta `H:MM` o `HH:MM` y se normaliza a `HH:MM` con el cero delante al confirmar). Solo se aplican al crear el mundo: una partida cargada continúa siempre en su fecha y hora guardadas.
- **Teclas**: **Alt+T/G/E/B/U** cambian de pestaña por su letra (también **Ctrl+Tab**/**Ctrl+Shift+Tab** para ciclar), **Alt+S** arranca y **Alt+C**/**Esc** cancela. El diálogo tiene fondo negro.

### Navegación y Vistas
- **Flechas de dirección (o h, j, k, l)**: Mover el cursor libremente o seguir una vía (estilo Vim).
- **Alt + Flechas (o Alt + h, j, k, l)**: Hacer zoom y rotar/panear la cámara.
- **Números (0-9)**: Introduce un salto numérico (multiplicador) para aumentar la velocidad y distancia del cursor. Al introducir un nuevo número se sobrescribe el anterior. Pulsa **Espacio** para volver a ir de 1 en 1.
- **Tecla 'a'**: Entrar en modo **Add** (Añadir). Esto te permite construir infraestructura rápidamente:
  - **n**: Construir Estación
  - **e**: Construir Sensor
  - **s**: Construir Semáforo
  - **g**: Construir Señal de Velocidad
- **Tecla 'o'**: Reubicar el cursor instantáneamente sobre el tren, estación, desvío, sensor o señal de velocidad seleccionado actualmente; una breve cruz blanca destella alrededor del cursor para localizarlo.
- **Tecla 'z'**: En 3D: Alternar entre las tres cámaras (Perspectiva, Cenital y Cabina). En 2D: Cambiar el tamaño de la zona muerta de la cámara.
- **Esc**: Abrir el menú **File** (`New`/`Load`/`Save`/`Settings`/`Quit`). Púlsala de nuevo para cerrar el menú.
- **Tecla 'Z' (Mayús+z)**: En 2D: Activar o desactivar la paginación de cámara.
- **Tecla 'Tab'**: Alternar entre los diferentes niveles de visibilidad de la barra de información (Compacta, Completa, Oculta).

### Modos de Juego y Atajos
LeTrain está fuertemente basado en modos. Al pulsar las siguientes teclas cambiarás el modo de interacción actual:
- **`r`**: **Modo Rails (Vías)** (Por defecto) - Construir y borrar vías.
- **`a`**: **Modo Add (Añadir)** - Añadir infraestructura (Estaciones, Sensores, Semáforos).
- **`t`**: **Modo Trains (Trenes)** - Construir nuevos trenes.
- **`d`**: **Modo Drive (Conducir)** - Conducir y controlar trenes.
- **`c`**: **Modo Link (Enganchar)** - Enganchar vagones y locomotoras.
- **`u`**: **Modo Unlink (Desenganchar)** - Desenganchar vagones y locomotoras.
- **`f`**: **Modo Forks (Desvíos)** - Gestionar y cambiar desvíos de vías.
- **`s`**: **Modo Semaphores (Semáforos)** - Gestionar semáforos.
- **`g`**: **Modo Speed Signals (Señales)** - Gestionar señales de límite de velocidad.
- **`n`**: **Modo Stations (Estaciones)** - Seleccionar e inspeccionar estaciones.
- **`e`**: **Modo Sensors (Sensores)** - Seleccionar e inspeccionar sensores.
- **`p`**: **Modo Programming (Programación)** - Abrir el editor para programar automatizaciones.
- **`:`**: **Modo CLI (Consola)** - Abrir la consola de comandos integrados.
- **`R`**: **Conmutar el modo Record/edición** (apagado por defecto). Al activarlo, el mundo (trenes, economía, descarrilamientos) se congela mientras estés en cualquier modo de edición (Rails, Add, Estaciones, Sensores, Semáforos, Señales, Desvíos, Trenes, consola o editor de programa), la construcción de vía se vuelve instantánea y cada edición se graba en el diario para el undo determinista y la exportación de escenarios. La simulación solo corre en modos de juego/vista (Drive, Menú, Link/Unlink) o cuando el toggle está apagado.

### Construcción (Modo Rails)
- **Mayús + Flechas (o H, J, K, L)**: Construir vías nuevas.
- **`a`**: Entrar en el **Modo Add** para colocar infraestructura: `n` **Estación**, `e` **Sensor**, `s` **Semáforo**, `g` **Señal de Velocidad**.
- **Números (0-9)**: Fijar un multiplicador de pasos para mover el cursor; pulsa **Espacio** para volver a 1.

### Mover Elementos de Vía
En los modos **Stations** (`n`), **Sensors** (`e`), **Semaphores** (`s`) y **Speed Signals** (`g`) puedes deslizar el elemento seleccionado a lo largo de la vía, en la dirección hacia la que mira el propio elemento:
- **Mayús + ↑ / Mayús + k**: mueve el elemento un hueco hacia delante por la vía.
- **Mayús + ↓ / Mayús + j**: mueve el elemento un hueco hacia atrás.

El elemento sigue la vía y atraviesa los desvíos (usando su rama activa), salta las posiciones ocupadas por otros elementos, nunca salta por encima de un tren y se detiene al final de la línea. Usa la **barra espaciadora** para invertir la dirección del elemento seleccionado (en el modo **Stations**, la barra espaciadora invierte la estación cuando no hay ningún tren parado en ella; en caso contrario inicia la carga/descarga). Si una estación de carga/descarga se aleja de su zona y pierde su influencia, se convierte en una estación genérica; un itinerario que le pida cargar o descargar será simplemente ignorado (el tren pasa de largo).

### Trenes y Conducción (Modo Drive / Trains)
- **Modo Trains**: Letras minúsculas crean vagones (teclas 1, 2, 3 para tipo de mercancía). Letras mayúsculas crean locomotoras (teclas 0-9 para color). Pulsa **Intro** para terminar.
- **Modo Drive**: Flechas Izquierda/Derecha seleccionan tren; si no había ninguno seleccionado al entrar, se selecciona automáticamente el primero disponible. Arriba/Abajo aceleran/frenan. **Espacio** invierte la marcha (solo en parado).
- **Modo Link / Unlink**: Arriba/Abajo selecciona el extremo del tren. Izquierda/Derecha selecciona la cantidad de vagones. **Espacio** ejecuta el enganche/desenganche.


### Modo Consola (CLI)
Pulsa la tecla `:` para abrir la consola integrada (al estilo Vim). Desde aquí puedes escribir comandos directos para construir vías, generar trenes o manipular entidades al instante.
Algunos comandos útiles:
- `go 10, 5;` - Mueve el cursor a una coordenada absoluta.
- `new st;` - Construye una estación bajo el cursor.
- `new loco A red;` - Crea una locomotora roja 'A'.
- `train 1 set engine on;` - Enciende el motor del tren 1.
- `ls st;` - Lista todas las estaciones.
- `quit;` o `q` - Sale del juego.

Para una referencia completa de los comandos, consulta **[grammar_es.md](grammar_es.md)**.

### Editor de Escenarios y Undo/Redo
- **`p`**: Abre el **LeTrain Editor** (editor de escenarios). Tiene tres pestañas — **Scenario**
  (`seed` + `on build` + `on start`), **Programming** (el script de automatización) y **Config**
  (ajustes del juego) — una referencia rápida por pestaña, y un pie con `Export`/`Import` de
  ficheros de escenario `.ltr`, `Reload from game`, `Apply program`, `Rebuild world` y `Close`.
  Save/Load se han movido al menú File (**Esc**). `Esc` cierra el editor. Ver
  **[scenarios_es.md](scenarios_es.md)**.
- **Modo Record/edición (`R`)**: mientras está activo, **`u`** deshace y **Ctrl+R** rehace
  ediciones (igual que `undo;` / `redo;`). Fuera de este modo, `u` es el modo **Unlink**
  (Desenganchar).

### Interacción
- **Espacio**: En el modo Semáforos, Señales de Velocidad o Sensores, invertir el sentido del dispositivo.
- **Tecla 'm'**: En Semáforos, cambia el estado (verde/rojo). En Señales de Velocidad, alterna el tipo de señal (Máx/Mín). En Trenes, enciende/apaga el motor.
- **Intro (Enter)**: Iniciar la carga/descarga de mercancías al detener el tren en una estación.

## 🏭 Tipos de Estaciones y Mercancías

En LeTrain, la logística es la clave de la economía. El mapa genera puntos de recursos naturales de forma procedural, y tu trabajo es conectarlos:

- **Estaciones de Carga (`▲`)**: Se construyen adyacentes a las zonas productoras (ej. minas de carbón, oro o rubíes). Extraen el material hacia tus trenes.
- **Estaciones de Descarga (`▼`)**: Se construyen junto a las zonas consumidoras (ej. ciudades o fábricas). Aquí vendes tu mercancía y obtienes beneficios.
- **Estaciones Genéricas (`◇`)**: Actúan como puntos de paso o intercambio, y se pueden asignar manualmente.

*(Nota visual: Cuando juegues en la Terminal 2D, las vías se dibujan con caracteres ASCII continuos y las vías muertas se marcan con iconos amarillos. Además, observarás un subrayado parpadeante en el tren mientras carga o descarga mercancía, que quedará fijo al completarse).*

## 💰 Economía y Beneficios

Empiezas con tu cuenta a cero. Para ganar dinero debes:
1. Construir vías desde una zona productora hasta una zona consumidora.
2. Construir un tren y sus vagones (¡cuidado con los costes de combustible!).
3. Transportar la mercancía. **El beneficio se calcula en base a la distancia recorrida**: cuanto más largo sea el trayecto desde la zona productora hasta la zona consumidora, ¡mayor será el pago!

Puedes ajustar los costes y recompensas base modificando el archivo `letrain.cfg`. Se distribuye
junto al juego; un `letrain.cfg` en el directorio de trabajo tiene prioridad sobre el empaquetado.
Además de los costes, el archivo incluye otros ajustes, como `ui.highlightBlockedTracks=true` (el
valor por defecto), que pinta las vías reservadas/bloqueadas con la librea del tren propietario en
ambos clientes; ponlo a `false` para conservar el color normal de la vía.

## 🕐 Reloj de Juego y Día/Noche

El mundo funciona con un reloj de juego lógico, independiente del tiempo real. El HUD muestra la fecha y hora de juego actuales (`D1 08:00`) tanto en la terminal 2D como en la vista 3D; una partida nueva empieza por defecto en el **día 1 a las 08:00**, y la pestaña **Game** del diálogo de Nueva Partida permite elegir otro **Start day** (1..366) y otra **Start time** (`HH:MM`) al crear el mundo. Un día de juego completo dura 24 minutos reales por defecto (ajustable en `letrain.cfg`). El reloj es determinista y guía la simulación, así que un mismo escenario se reproduce siempre con las mismas horas.

- **Ciclo día/noche**: Se simulan el amanecer y el atardecer, y la paleta del mundo pasa por día, crepúsculo y noche en ambos clientes: luz ambiental, luz solar, cielo y colores del terreno en 3D, y la paleta de colores de la terminal en 2D. La misma red se ve muy distinta a medianoche.
- **Faros**: Los faros de las locomotoras solo brillan cuando oscurece y con el motor en marcha — si se apaga el motor, se apagan. Las dos lámparas siguen visibles (apagadas) a cualquier hora, y solo alumbra la locomotora de cabeza del tren: las demás mantienen sus faros apagados.
- **Comandos del reloj** (sintaxis completa en **[grammar_es.md](grammar_es.md)**):
  - `time;` - Muestra la fecha y hora de juego actuales.
  - `time set HH[:MM];` - Salta el reloj, p. ej. `time set 21:30;` o `time set 9;` para las 09:00. Las horas fuera de rango se rechazan con un aviso y el reloj no se mueve.
- **Los horarios de los itinerarios usan este reloj**: Las horas `arrival` y `departure` de un waypoint se miden con el reloj de juego, así que `time set` es la forma rápida de probar un horario sin esperar a que pase el día. Ver **Autopilot y Rutas** más abajo, **[grammar_es.md](grammar_es.md)** y **[scenarios_es.md](scenarios_es.md)**.
- **Disparadores temporales (`at` / `every`)**: el programa puede automatizar acciones por el reloj de juego, no solo por eventos: `at 6:30 { semaphore 1 open; }` abre la línea cada mañana y `every 30m { … }` repite un bloque en una rejilla fija (con `from` opcional para anclar la rejilla). Usan el mismo bloque de acciones que los triggers de eventos, así que las acciones de tren van con referencia explícita (`train 5 …` o `train at station "Mina" load;`). Los límites (64 activos) y duplicados avisan, y una acción no permitida avisa al dispararse y se ignora. Cargar una partida **no recupera las horas perdidas**: un `at` ya pasado espera al día siguiente y `every` continúa en su siguiente punto de rejilla. La pausa de edición congela el reloj: los disparos no se acumulan. Sintaxis completa en **[grammar_es.md](grammar_es.md)** §4.

## 🤖 Autopilot y Rutas

Para gestionar decenas de trenes sin volverte loco, LeTrain incluye un Piloto Automático que puedes programar tú mismo. Consulta la guía completa de programación en: **[grammar_es.md](grammar_es.md)**.

Los itinerarios también admiten **horarios y maniobras de enganche y apartado**: cada waypoint puede llevar una hora de `arrival` y/o `departure` (reloj de 24 h). Al llegar se ejecutan las acciones del waypoint, el tren **espera hasta su salida** y entonces parte; si las acciones terminan tarde, sale tarde y el retraso queda registrado. Un servicio diario recurrente puede terminar con `park` (frena, apaga el motor y mantiene el piloto automático armado) y volver a arrancar solo en su siguiente salida programada. La puntualidad se informa por parada en `info train N`: desviación de llegada/salida en minutos de juego, más la desviación actual, media y máxima. La línea de estado del tren seleccionado en el HUD muestra el desfase actual como valor firmado compacto (`Punct: +2`, `+` tarde / `−` adelantado); hasta que no se mide una parada programada no muestra nada. Los waypoints también pueden encadenar maniobras, como `uncouple`/`couple … all` y `stop on contact`, para cambios de sentido y apartado. Consulta la sección **Autopilot e Itinerarios** de **[grammar_es.md](grammar_es.md)** y la guía de escenarios **[scenarios_es.md](scenarios_es.md)** para la sintaxis completa y ejemplos.

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
