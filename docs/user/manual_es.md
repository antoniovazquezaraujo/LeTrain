# LeTrain - Manual de Usuario

Bienvenido al manual oficial de **LeTrain**. Aquí encontrarás todo lo que necesitas saber para operar tu red ferroviaria, gestionar la economía y automatizar tus trenes, ya sea en el cliente clásico de terminal 2D o en la moderna vista 3D.

## 🎥 Videotutorial / Gameplay

<iframe width="1120" height="630" src="https://www.youtube.com/embed/VwS9Gbu3ygw" title="LeTrain Trailer" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>



---

## 🎮 Controles Básicos

El juego se puede controlar tanto en su versión 2D como en la 3D mediante el teclado. A continuación, las teclas y modos principales:

### Navegación y Vistas
- **Flechas de dirección (o h, j, k, l)**: Mover el cursor libremente o seguir una vía (estilo Vim).
- **Números (0-9)**: Introduce un salto numérico (multiplicador) para aumentar la velocidad y distancia del cursor. Al introducir un nuevo número se sobrescribe el anterior. Pulsa **Espacio** para volver a ir de 1 en 1.
- **Tecla 'o'**: Reubicar el cursor instantáneamente sobre un tren, estación o desvío.
- **Tecla 'z'**: En 3D: Alternar entre las tres cámaras (Perspectiva, Cenital y Cabina). En 2D: Cambiar el tamaño de la zona muerta de la cámara.
- **Esc**: En 3D: Salir del menú del juego.
- **Tecla 'Z' (Mayús+z)**: En 2D: Activar o desactivar la paginación de cámara.
- **Tecla 'Tab'**: Alternar entre los diferentes niveles de visibilidad de la barra de información (Compacta, Completa, Oculta).

### Construcción (Modo Rails)
- **Mayús + Flechas (o H, J, K, L)**: Construir vías nuevas.
- **Ctrl + Flechas (o Ctrl + h, j, k, l)**: Borrar vías existentes.
- **Inicio (Home)**: Crear un semáforo sobre la vía.
- **Insert**: Crear un sensor sobre la vía.
- **Fin (End)**: Crear una estación.
- **Supr (Del)**: Crear una señal de límite de velocidad en la vía.

### Trenes y Conducción (Modo Drive / Trains)
- **Modo Trains**: Letras minúsculas crean vagones (teclas 1, 2, 3 para tipo de mercancía). Letras mayúsculas crean locomotoras (teclas 0-9 para color). Pulsa **Intro** para terminar.
- **Modo Drive**: Flechas Izquierda/Derecha seleccionan tren. Arriba/Abajo aceleran/frenan. **Espacio** invierte la marcha (solo en parado).
- **Modo Link / Unlink**: Arriba/Abajo selecciona el extremo del tren. Izquierda/Derecha selecciona la cantidad de vagones. **Espacio** ejecuta el enganche.


### Modo Consola (CLI)
Pulsa la tecla `:` para abrir la consola integrada (al estilo Vim). Desde aquí puedes escribir comandos directos para construir vías, generar trenes o manipular entidades al instante.
Algunos comandos útiles:
- `go 10, 5;` - Mueve el cursor a una coordenada absoluta.
- `new st;` - Construye una estación bajo el cursor.
- `new loco A red;` - Crea una locomotora roja 'A'.
- `train 1 set engine on;` - Enciende el motor del tren 1.
- `ls st;` - Lista todas las estaciones.
- `quit;` o `q` - Sale del juego.

Para una referencia completa de los comandos, consulta la documentación o escribe `info` en la consola.

### Interacción
- **Espacio**: En el modo Semáforos o Señales de Velocidad, invertir el sentido del dispositivo.
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

Puedes ajustar los costes y recompensas base modificando el archivo `economy.properties` antes de lanzar el juego.

## 🤖 Autopilot y Rutas

Para gestionar decenas de trenes sin volverte loco, LeTrain incluye un Piloto Automático que puedes programar tú mismo. Consulta la guía completa de programación en: **[grammar_es.md](grammar_es.md)**.

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
