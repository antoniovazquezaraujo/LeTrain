
[🇬🇧 Read in English](index.md)
<div style="display: flex; justify-content: center; width: 100%; overflow: hidden; margin-top: 20px; margin-bottom: 20px;">
<pre style="font-size: 1.2em; line-height: 1.2; font-weight: bold; background: transparent; border: none; overflow: hidden; padding: 0; text-align: left;">
      __       ______           _
     / /   ___/_  __/________ _(_)___
    / /   / _ \/ / / ___/ __ `/ / __ \
   / /___/  __/ / / /  / /_/ / / / / /
  /_____/\___/_/ /_/   \__,_/_/_/ /_/
       The Letter Train Simulator           
   (C) 2006-2026 Antonio Vazquez Araujo
</pre>
</div>

<p align="center">
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/releases/latest">
    <img src="https://img.shields.io/badge/🎮_Descargar_Última_Versión-0078D4?style=for-the-badge&logo=github&logoColor=white" alt="Descargar Última Versión">
  </a>
  <a href="https://avaraujo.itch.io/letrain-procedural-tycoon">
    <img src="https://img.shields.io/badge/👾_Disponible_en_Itch.io-FA5C5C?style=for-the-badge&logo=itch.io&logoColor=white" alt="Disponible en Itch.io">
  </a>
  <a href="https://snapcraft.io/letrain">
    <img src="https://img.shields.io/badge/🐧_Bajar_en_Snap_Store-E95420?style=for-the-badge&logo=snapcraft&logoColor=white" alt="Bajar en Snap Store">
  </a>
</p>

**LeTrain** es un simulador de trenes procedimental que combina la estética clásica ASCII con el renderizado moderno en 3D. Originalmente un pequeño experimento en C++, ha evolucionado hasta convertirse en un simulador completo en Java donde los jugadores gestionan redes ferroviarias, logística y economía en un mundo infinito generado de forma procedimental.

## ✨ Características

- **Motores de Renderizado Duales:**
  - **Vista Moderna 3D:** Construida con [LibGDX](https://libgdx.com/), ofreciendo un entorno 3D dinámico y totalmente navegable con zoom, rotación y un HUD completo.
  - **Terminal Clásica 2D:** Impulsada por [Lanterna](https://github.com/mabe02/lanterna), ofreciendo una estética retro puramente ASCII para los amantes de las terminales clásicas.
  
- **Mundo Procedimental Infinito:** El terreno se genera dinámicamente usando ruido Perlin, creando paisajes interminables para explorar y construir.

- **Automatización Avanzada e IDE:**
  - Incluye un lenguaje de programación integrado (analizado mediante ANTLR4) para una verdadera automatización.
  - Cuenta con un entorno de desarrollo integrado (IDE) directamente dentro del juego para escribir, compilar y ejecutar scripts lógicos para los trenes.
  - Automatiza desvíos, configura rutas con piloto automático y gestiona itinerarios complejos.

- **Economía y Logística:** Construye vías, gestiona recursos y equilibra tu presupuesto. El sistema de economía central reacciona a tus decisiones.

## 🚀 Inicio Rápido

### Compilando desde el Código Fuente

LeTrain requiere **Java 17+** y **Maven**.

```bash
# Clonar el repositorio
git clone https://github.com/antoniovazquezaraujo/LeTrain.git
cd LeTrain

# Compilar y empaquetar el proyecto
mvn clean package -DskipTests
```

El proceso de compilación usa `jpackage` para generar lanzadores nativos independientes en la carpeta `output/LeTrain`.

### Ejecutando el Juego

Puedes ejecutar tanto la versión moderna 3D como la clásica 2D:

- **Motor 3D:** Ejecuta el archivo `LeTrain`.
- **Terminal 2D:** Ejecuta el archivo `LeTrain2D`.

## 📖 Documentación

- [Manual de Usuario](manual_es.md)
- [Gramática de Scripting y Automatización](grammar_es.md)

## 🎥 Multimedia y Tutoriales

### Navegación y Cámara
- **Navegación Básica:** El cursor se mueve con las teclas de dirección (o teclas de Vim h, j, k, l) por todo el mapa. Si entra en una vía, la sigue automáticamente, tanto hacia adelante como hacia atrás.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/7fKXj1krkFk" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/eidXDfMM5Ec" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Zoom y Paneo de Cámara:** Se puede hacer zoom con Alt y las flechas (o `Alt + h, j, k, l`), así como girar la cámara. También hace zoom la rueda del ratón.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/hWSTbSypcNo" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/3djcZgXX7h8" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Descubrir Mapa:** Según el usuario va navegando por el mapa, se van mostrando nuevas zonas que estaban por descubrir.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/SsmqqXQSOQo" frameborder="0" allowfullscreen></iframe></div>
- **Movimiento del Cursor:** Para acelerar el movimiento del cursor, se puede teclear un número. Después de eso, el cursor se moverá en distancias mayores. Para volver a moverse de 1 en 1 usar la barra espaciadora.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/Xu21CMRjhuw" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/0EMS3kzGMNg" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Reubicar Cursor:** Cuando se necesita traer el cursor a donde está un tren, una estación o un desvío se puede usar la tecla `o`.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/PUJ00irsU5s" frameborder="0" allowfullscreen></iframe></div>

### Construyendo la Red
- **Creación de Vías:** Para crear vías se usa la tecla `Mayus` y las flechas (o `Shift + H, J, K, L`). En los finales de vía se muestran unos bloques amarillos que desaparecen cuando las vías se unen.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/PnIWZxik3Ds" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/00y-E6YBxIc" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Creación de Desvíos:** Para crear un desvío basta con moverse por una vía ya existente y salirse de la misma. El desvío crea automáticamente los semáforos necesarios. También se crea cuando se entra en una vía existente desde fuera. Solo se crean en ángulos de 45 grados y si, al entrar en la vía se gira para continuar por ella. Si se sigue de largo, simplemente se crean cruces.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/iVHsKuj0Xm8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/mYqpJxpBA3k" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Borrar Vías:** Para retirar vías se usa la tecla `Ctrl` con las flechas de dirección (o `Ctrl + h, j, k, l`), tanto hacia adelante como hacia atrás. El cursor va siguiendo la vía automáticamente.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/TDF-26e1wqI" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/L_FrO48OlZY" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Túneles y Puentes:** Cuando se crean vías sobre el agua, se crean puentes. Cuando se crean vías dentro de montañas de crean túneles. En modo `Drive` no se ve dentro de los túneles. En modo `Rails` sí.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/bn8isLuMq8Y" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/_L_J0J8-_m4" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Tipos de Terreno:** Hay tres tipos de terreno distintos: el suelo normal, de color verde, el agua, de color azul y la montaña, de color marrón. Construir vías sobre el agua y bajo la montaña es más caro.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/uf-9XmoXH8E" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/4d81Dt1iTaM" frameborder="0" allowfullscreen></iframe></div>
</div>

### Gestión de Trenes
- **Crear Trenes:** Para crear trenes se usa el modo `Trains`. En este modo, las letras minúsculas crean vagones. Antes de crearlos, se puede cambiar su tipo usando los números: 1 para oro, 2 para carbón y 3 para rubí. En cada tipo de vagón solo se podrá cargar ese tipo de mercancía. Para crear locomotoras se usan las letras mayúsculas. Justo después de crear una locomotora se puede cambiar su color tecleando un número del 0 al 9. Se puede cambiar varias veces. Cuando se pulsa intro se termina la creación del tren.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/KlsM5MXg8Sg" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/7zhFuPuNfwY" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Seleccionar Trenes:** En modo `Drive` se puede conducir un tren, es decir: acelerar y decelerar. Para seleccionar el siguiente tren o el anterior, se usan las flechas izquierda y derecha. El tren seleccionado muestra una linea verde encima.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/M-osKFq_xgE" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/iZisYx3nR8U" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Mover Trenes:** Para mover un tren en modo `Drive` se usan las flechas arriba y abajo para acelerar y decelerar respectivamente. También la barra espaciadora para invertir su sentido, pero solo cuando está detenido. En la parte inferior izquierda de la pantalla se ve un velocímetro, que muestra una linea con la velocidad solicitada y otra con la velocidad alcanzada. Entre ambas hay un retardo debido a la inercia del tren.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/_rC-jeLiWSs" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/S8dJ6oAbT3k" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Cámara del Maquinista (Cab View):** Hay tres cámaras: la cámara en perspectiva, la cámara cenital y la cámara de locomotora. Las tres se cambian con la tecla `c`.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/sCCPhXLhoeo" frameborder="0" allowfullscreen></iframe></div>
- **Enganchar y Desenganchar:** Para enganchar y desenganchar vagones y locomotoras, se usan los modos `link` y `unlink`. En estos modos, primero se selecciona desde qué extremo se desea operar, con las flechas arriba y abajo. Luego, con las teclas izquierda y derecha se selecciona la cantidad de vehículos que se desean enganchar o desenganchar. Finalmente, la barra espaciadora ejecuta la operación. Una locomotora no puede empujar un vehículo a menos que esté enganchada a él. Estas operaciones solo pueden hacerse con el tren detenido.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/StOnossHNFA" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/CK-gKHcVLTE" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Accidentes:** Los trenes pueden hacer contacto a bajas velocidades. Cuando la velocidad es alta, simplemente, adiós tren.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/9tBCqFEWy74" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/f0ra9mr-pWQ" frameborder="0" allowfullscreen></iframe></div>
</div>

### Logística y Estaciones
- **Seleccionar Estación:** Las estaciones se seleccionan con el modo `stations` con las teclas izquierda y derecha.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/W8lSjmPWKv8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/Z6syPCHnpzA" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Zonas de Carga y Descarga:** Para cargar mercancías se necesitan las estaciones. Hay tres tipos de zonas de carga y descarga: oro, de color amarillo; carbón, de color negro y rubí, de color rojo. Las zonas de carga son cubos y las de descarga rectángulos en el suelo.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/SsvqDmw31ck" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/gZSVIpeFrf4" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Estaciones de Logística:** Al crear una estación suficientemente cerca de una zona de carga o descarga, el tren podrá cargar o descargar mercancía, si lleva algún vagón del mismo tipo que la zona.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/ojiHCBCYSj8" frameborder="0" allowfullscreen></iframe></div>
- **Acciones de Carga y Descarga:** Solo se puede cargar o descargar con el tren parado y se hace usando la tecla `Intro`. Durante la carga, el letrero de la estación se muestra intermitente y se puede observar cómo los vagones van cargándose o descargándose.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/y1hJVD04nX8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/zQh3w74xfB8" frameborder="0" allowfullscreen></iframe></div>
</div>

### Seguridad y Automatización
- **Crear Semáforos:** Los semáforos se crean cuando el cursor está sobre la vía y se usa la tecla `Inicio`. En el modo `semaphores` se seleccionan con las flechas horizontales, se cambia su estado con la tecla 'm', y se invierte su dirección con la barra espaciadora.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/VMJCvCAxExM" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/UiT4tzuuw9A" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Crear Sensores:** Los sensores permiten automatizar tareas, como detener un tren cuando pasa por encima, cambiar un desvío o un semáforo, etc. Se crean cuando el cursor está sobre la vía usando la tecla `Insert`.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/twaJryI_fKs" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/0R8XAXsHmt8" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Seguridad (Cantones):** Entre cada cambio de vía se crea un cantón, como en el mundo real, que representa una zona que un tren puede bloquear para que ningún otro pueda entrar. Cuando un tren avanza en piloto automático, es decir, cuando se le ha asignado un itinerario, va bloqueando el cantón en el que entra y el siguiente. Si no consigue bloquear el siguiente porque ya está ocupado, entonces frena, para no chocar. Se deben calcular las distancias necesarias de frenado para evitar que un tren acabe invadiendo un cantón bloqueado. En ese caso, se frenan los dos automáticamente y pasan al modo manual.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/p7L3IR3CbWI" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/26AmqDbMsUk" frameborder="0" allowfullscreen></iframe></div>
</div>
- **IDE de Programación:** En el modo `program` se tiene acceso a un sencillo IDE que permite programar acciones y eventos. Se pueden crear itinerarios y asignarlos a trenes. Se puede hacer que un itinerario contenga varias estaciones o sensores y que cuando el tren pase por ellos realice ciertas tareas, como cargar, descargar, invertirse o detenerse.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/ke0XuMDk3iM" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/z6CGDnxDqa0" frameborder="0" allowfullscreen></iframe></div>
</div>

### Archivo Histórico (Early Alpha)
Mira cómo empezó LeTrain en esta antigua grabación del emulador 2D:

<div><strong>2D Terminal</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/2WVScFIG4_E" frameborder="0" allowfullscreen></iframe></div>

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
