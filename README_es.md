# LeTrain 🚂✨

<p align="left">
  🌐 <a href="README.md">English</a> | <strong>Español</strong>
</p>

<p align="left">
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/actions/workflows/ci.yml"><img src="https://github.com/antoniovazquezaraujo/LeTrain/actions/workflows/ci.yml/badge.svg" alt="Estado de la compilación"></a>
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/releases"><img src="https://img.shields.io/github/v/release/antoniovazquezaraujo/LeTrain?include_prereleases&style=flat-square" alt="Release en GitHub"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/Licencia-Apache_2.0-blue.svg?style=flat-square" alt="Licencia"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
</p>

```text
      __       ______           _
     / /   ___/_  __/________ _(_)___
    / /   / _ \/ / / ___/ __ `/ / __ \
   / /___/  __/ / / /  / /_/ / / / / /
  /_____/\___/_/ /_/   \__,_/_/_/ /_/
       The Letter Train Simulator           
   (C) 2006-2026 Antonio Vázquez Araujo 
```

<p align="center">
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/releases/latest">
    <img src="https://img.shields.io/badge/🎮_Descargar_Última_Versión-0078D4?style=for-the-badge&logo=github&logoColor=white" alt="Descargar Última Versión">
  </a>
  <a href="https://avaraujo.itch.io/letrain-procedural-tycoon">
    <img src="https://img.shields.io/badge/👾_Disponible_en_Itch.io-FA5C5C?style=for-the-badge&logo=itch.io&logoColor=white" alt="Disponible en Itch.io">
  </a>
  <a href="https://snapcraft.io/letrain">
    <img src="https://img.shields.io/badge/🐧_Consíguelo_en_Snap_Store-E95420?style=for-the-badge&logo=snapcraft&logoColor=white" alt="Consíguelo en Snap Store">
  </a>
  <br><br>
  <a href="docs/PHILOSOPHY_es.md">
    <img src="https://img.shields.io/badge/📜_Manifiesto_y_Filosofía-8A2BE2?style=for-the-badge&logo=markdown&logoColor=white" alt="Leer el Manifiesto">
  </a>
  <a href="https://antoniovazquezaraujo.github.io/LeTrain/">
    <img src="https://img.shields.io/badge/📖_Leer_Documentación-2EA043?style=for-the-badge&logo=markdown&logoColor=white" alt="Leer Documentación">
  </a>
</p>

<p align="center">
  <img width="80%" alt="promoLeTrain2D" src="https://github.com/user-attachments/assets/4b435726-b68f-47bb-9487-84a61a3ea568" />
</p>

**LeTrain** es un simulador ferroviario procedural que fusiona la estética clásica de caracteres ASCII con un motor 3D moderno. Construye extensas redes de vías a lo largo de un mundo infinito, gestiona una economía dinámica y domina el arte de la eficiencia logística.

> 📜 **[Leer el Manifiesto y Filosofía de Diseño de LeTrain](docs/PHILOSOPHY_es.md)**  
> *"Un juego para niños ingenieros e ingenieros niños: sin juguetes que juegan solos, con trenes reales y la sobriedad del teclado."*

---

## 🌟 Novedad en 2026: La Evolución 3D
¡LeTrain ha evolucionado! Manteniendo sus raíces de terminal, ahora cuenta con un **motor 3D de alto rendimiento** impulsado por LibGDX:
- **Perspectiva moderna**: Explora tu imperio ferroviario en 3D con cámaras orbitales suaves.
- **Alma clásica**: Las zonas industriales y los trenes conservan su encanto icónico basado en caracteres, ahora proyectados como esculturas físicas tridimensionales.
- **Acabado visual pulido**: Transparencias dinámicas en montañas, estaciones codificadas semánticamente por colores y tipografía de alta resolución.

## 🎬 Grabar, Deshacer y Escenarios
El flujo de trabajo de edición es de primer nivel, tanto en el cliente 2D como en el 3D:
- **Modo grabación/edición (`R`)**: Congela el mundo, construye al instante y registra cada cambio en un diario de acciones.
- **Deshacer / Rehacer**: `u` y **Ctrl+R** (o `undo;` / `redo;`), tanto para construcciones como para cambios de estado (agujas, semáforos, señales).
- **Escenarios (`.ltr`)**: Exporta toda tu red como una pequeña receta de texto e importa la de cualquier otra persona. La misma semilla reconstruye el mismo mundo, por lo que un escenario viaja en texto plano. Valídalo sin entorno gráfico mediante la herramienta incluida **`letrain-check`**.
- **Editor integrado (`p`)**: Un editor por pestañas para Escenario / Programa / Configuración con referencia rápida por pestaña y navegación de errores.
- **Modo experimento (`X`)**: Guarda una instantánea del mundo en vivo, prueba cualquier idea audaz y restáurala con una sola pulsación.

<p align="center">
  <img width="80%" alt="El editor de LeTrain con la pestaña de configuración seleccionada" src="docs/user/images/config-editor.png" />
</p>

## 💰 Economía e Industria Profunda
El juego incluye un sistema financiero totalmente integrado:
- **Gestión de capital**: Comienza con **$0** (desafío Zero-to-Hero) y haz crecer tu patrimonio.
- **Motor logístico**: Transporta cargas especializadas (**ORO**, **CARBÓN**, **RUBÍ**) desde Productores hasta Consumidores.
- **Precios dinámicos**: Los ingresos escalan según la distancia recorrida. ¡Cada metro cuenta!
- **Costes de construcción**: Equilibrio entre la expansión de la infraestructura y el consumo de combustible de las locomotoras.
- **Jugabilidad configurable**: Ajusta todo el juego mediante el archivo `letrain.cfg` (se incluye junto a los ejecutables).

## 🌍 Mundo Procedural Infinito
- **Potenciador de entropía**: Cada partida nueva comienza en coordenadas aleatorias únicas (de ±10k a ±100k unidades) para garantizar exploración fresca siempre.
- **Paisajes procedurales**: Navega por lagos, bosques y altas montañas generadas por ruido de Perlin multicapa.
- **Frustum Culling**: Motor de renderizado optimizado para gestionar mapas inmensos procesando únicamente lo que está a la vista.

---

## 🚀 Cómo Empezar

### Requisitos previos
- **Java JDK 17** o superior.
- **Maven**.

### Compilación rápida
El sistema de compilación está completamente automatizado. Simplemente ejecuta:
```bash
mvn clean package -DskipTests
```
Esto genera tres distribuciones independientes en `output/`:
- **`output/LeTrain`** — La experiencia moderna en **3D**.
- **`output/LeTrain2D`** — La clásica y ultrarrápida vista en **terminal ASCII**.
- **`output/letrain-check`** — El validador de escenarios (`.ltr`) en línea de comandos.

Junto a cada ejecutable se incluye un archivo `letrain.cfg` por defecto para que puedas personalizar el juego sin tocar el código.

### Lanzadores
- **`LeTrain`** (o `.exe` / `.sh`): Inicia directamente en **modo 3D**.
- **`LeTrain2D`** (o `.exe` / `.sh`): Para quienes prefieren la vista clásica en terminal.
- **`letrain-check <fichero.ltr>`**: Valida un fichero de escenario e imprime diagnósticos con formato `fichero:línea:columna`.

---

## 🛠️ Stack Tecnológico
- **Núcleo**: Java 17
- **Motor 3D**: LibGDX (OpenGL)
- **UI Terminal**: Lanterna
- **Scripting**: Lenguaje propio de automatización ferroviaria basado en ANTLR4.
- **Audio**: Motor de síntesis granular para sonidos realistas de locomotoras.

## 🤝 Cómo Colaborar
¡Las contribuciones son bienvenidas! Informes de errores, ideas para nuevas características, documentación y código son siempre apreciados.
- 📜 **Empieza leyendo nuestro [Manifiesto y Filosofía de Diseño](docs/PHILOSOPHY_es.md)** para comprender el alma del juego, la simetría 2D/3D y los principios innegociables de LeTrain.
- Consulta **[CONTRIBUTING.md](CONTRIBUTING.md)** para el flujo de compilación, tests y Pull Requests.
- [Abre un issue](https://github.com/antoniovazquezaraujo/LeTrain/issues/new/choose) para reportar un error o proponer una mejora.
- Para cambios en el código, crea una rama desde `develop` (`feature/...` / `fix/...`), mantén el PR enfocado y asegúrate de que `mvn clean test` pasa con éxito.

---
*Creado por Antonio Vázquez Araujo.*  
[Twitter/X - @avaraujo](https://twitter.com/avaraujo) | [GitHub](https://github.com/antoniovazquezaraujo)

## ⚠️ Problemas Conocidos

### Limitaciones del paquete Snap (WSL2 y Escritorio Remoto)
Dado que el **paquete Snap** utiliza un aislamiento estricto y empaqueta sus propias librerías de gráficos y audio, puede presentar incidencias en entornos virtualizados o remotos:

* **Subsistema de Windows para Linux (WSL2 / WSLg):** La versión Snap se ejecutará sin sonido debido a una restricción conocida de AppArmor que impide a Snap acceder al socket no estándar de PulseAudio de Microsoft (`/mnt/wslg/PulseServer`).
* **Escritorio Remoto (XRDP / VNC):** 
  * La **versión 3D** fallará al arrancar con errores de OpenGL (`Failed to create context: BadValue`, fallos del driver software `swrast`). Los drivers Mesa de Snap entran en conflicto con la pantalla virtual X11 creada por XRDP.
  * La **versión 2D** no emitirá audio a través de la red. ALSA accede directamente a la tarjeta de sonido física en vez de rutearlo por los sumideros virtuales de PulseAudio de XRDP.

**Solución:** Si juegas a través de una conexión de escritorio remoto en Linux, compila el juego desde el código fuente o utiliza el lanzador `.sh` del archivo `.zip` de la release para Linux, que se adapta perfectamente a los drivers de XRDP del sistema anfitrión. Si juegas en Windows, descarga el archivo nativo **`LeTrain-Windows.zip`** desde la página de [Releases](https://github.com/antoniovazquezaraujo/LeTrain/releases) para contar con aceleración 3D y sonido impecables.
