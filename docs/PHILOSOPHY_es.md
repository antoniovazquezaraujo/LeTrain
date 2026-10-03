# Manifiesto de LeTrain: Filosofía, Estética y Leyes de Diseño

> [English](PHILOSOPHY.md) | **Español**

> *"Un simulador logístico procedural donde la sobriedad del ASCII y la ingeniería ferroviaria se encuentran con la libertad de la automatización."*

---

## 0. Por qué: La caja de cartón y el ingeniero niño

LeTrain es, ante todo, un juego para **"niños ingenieros"** o para **"ingenieros niños"**.

> *"De pequeño tenía algunos juguetes mal diseñados con los que lo único que podías hacer era observar cómo el juguete 'jugaba solo'. Eso acababa por aburrirme y hacer que jugara con la caja de cartón que lo contenía, convirtiéndola en mi imaginación en un tráiler de 10 ejes que avanzaba pesadamente por la alfombra cargando una zapatilla-nave-espacial hacia la plataforma de despegue.*
>
> *La idea de LeTrain es permitir que el usuario despliegue su imaginación e invente, que desarrolle las posibilidades infinitas de un sistema programable, que comparta sus avances, que disfrute sin sentirse abrumado por miles de opciones de cada cosa ni contrariado por mecanismos falsos que solo parece que hacen algo pero en el fondo no lo hacen.*
>
> *En LeTrain los trenes son 'reales', y hay que llevarlos a su destino. No son personajes de adorno. La dualidad 2D - 3D es precisamente lo que nos debe forzar a mantenerlo simple, conceptual, claro."*
> — **Antonio Vázquez Araujo**, Creador de LeTrain

---

## 1. Misión y Esencia: Juguetes que no juegan solos

LeTrain nació como un homenaje y una evolución de los grandes simuladores clásicos de transporte y logística, concebido desde la óptica de la ingeniería limpia, la imaginación y la elegancia conceptual.

* **El rechazo al juguete que juega solo:** LeTrain no es un salvapantallas interactivo, ni un juego de observación pasiva, ni un catálogo de microgestión abrumadora. El jugador es un diseñador de redes: si el sistema funciona, es porque el jugador ha tendido las vías, asegurado los cantones o programado el itinerario.
* **Trenes reales, no atrezo:** Cada tren tiene masa, longitud, inercia, cantón reservado y mercancía real en sus vagones. No son partículas estéticas circulando en bucles pregrabados: son máquinas operativas que deben llegar a su destino físico.
* **Mecanismos honestos frente a mecanismos falsos:** Si una aguja conmuta, si un semáforo cierra un cantón o si una estación almacena carbón, esa acción ocurre de verdad en el grafo de la simulación. En LeTrain no existen estadísticas infladas ni mecánicas de fachada que simulen una complejidad inexistente.
* **Líneas rojas inviolables:**
  * **Sin mecánicas predatorias:** Jamás habrá microtransacciones, compras integradas, anuncios, tiempos de espera artificiales ("espera 2 horas para construir esta vía") ni sistemas de recompensas diseñados para explotar la psicología del jugador.
  * **Sin complejidad cosmética artificial:** Cada elemento en pantalla responde a una entidad del modelo. No se introducen elementos visuales barrocos que enturbien la legibilidad del sistema.
  * **Código abierto y soberanía del usuario:** El jugador y la comunidad son dueños absolutos de su experiencia. Las partidas, los escenarios y las configuraciones viajan en texto plano legible, editable y verificable.

---

## 2. La Dualidad Estética: Dos Dimensiones, Un Solo Corazón

Una de las señas de identidad más singulares de LeTrain es su bicefalia visual: dos clientes completamente independientes compartiendo un único núcleo lógico invariable.

> **Principio de Diseño:** *La dualidad 2D-3D es el ancla que nos obliga a mantener el juego simple, conceptual y claro.* Si una idea no puede expresarse con nitidez en una rejilla ASCII, probablemente esté sobrediseñada.

### El 2D de Terminal (Lanterna)
* **No es un modo retro ni un compromiso técnico:** Es un ciudadano de primera clase. La interfaz de terminal proporciona una inmediatez, un rendimiento y una densidad de información inigualables.
* **La dignidad del carácter ASCII:** Cada glifo (`=`, `|`, `/`, `\`, `+`, `#`) tiene un peso espacial y un significado funcional inequívoco. Evoca la atmósfera austera y rigurosa de los paneles de Control de Tráfico Centralizado (CTC) de las grandes estaciones del siglo XX.

### El 3D Dinámico (LibGDX)
* **El glifo como escultura espacial:** El motor tridimensional no sustituye el lenguaje ASCII, sino que lo proyecta físicamente en tres dimensiones. Los caracteres son bloques y vías suspendidas en un relieve infinito generado por ruido de Perlin.
* **Simetría funcional absoluta:** Jamás existirá una acción posible en 3D que no pueda realizarse en 2D, ni viceversa. Lo que ocurre en una dimensión es matemáticamente idéntico a lo que ocurre en la otra. Ambas vistas son meros visitantes (`Visitor pattern`) que observan el mismo modelo inmutable.

### La Ergonomía del Teclado y el Guiño al «Viejo Hacker» (Vim & Consola)
LeTrain rinde un homenaje explícito a la cultura de los viejos hackers de terminal, los editores clásicos (`vi/vim`), los *roguelikes* seminales y los sistemas Unix donde las manos jamás necesitan despegarse de la fila central (*home row*) del teclado.

* **Navegación natural con `h, j, k, l`:** Desplazar el cursor por el mapa, recorrer las vías o ajustar la vista no requiere desviar la mano hacia el ratón ni hacia las flechas de dirección. La navegación se convierte en un reflejo de memoria muscular inmediato.
* **El Modo Consola y la Velocidad del Pensamiento:** Junto con los atajos de teclado, la consola integrada permite consultar estados, conmutar agujas o gobernar trenes en un par de pulsaciones precisas, evitando la molestia de rebuscar en menús flotantes o ventanas modales abarrotadas.
* **Inmersión y Estado de Flujo (*Flow*):** Esta decisión no es un mero adorno nostálgico; es ergonomía pura. Al eliminar la fricción del ratón, el jugador entra en un estado de concentración ininterrumpido donde la red ferroviaria se manipula a la misma velocidad a la que se piensa.

---

## 3. Las Leyes Físicas y el Motor Mecánico

El backend de LeTrain se rige por principios de ingeniería de software que garantizan estabilidad a gran escala y fidelidad operativa.

* **Reactividad por Eventos vs. Polling Ciego:**
  * *La Regla Sagrada del Bucle:* Ningún componente debe iterar ciegamente sobre todas las vías, vagones o elementos en cada tick del juego para comprobar colisiones o reservar cantones.
  * Las decisiones nacen en los límites: cuando la cabeza de un tren pisa un sensor o entra en una aguja, se reservan los cantones; cuando la cola abandona el nodo, se liberan. Esta reactividad permite simular redes inmensas con un consumo de recursos despreciable.
* **La Verdad del Ferrocarril (Cantones y Señales):**
  * La seguridad de los trenes no se basa en trucos de teletransporte o "fantasmas" que atraviesan otros vehículos. El sistema de cantones y grafos topológicos (`RailwayGraph`, `BlockManager`) es la ley inquebrantable que previene desastres.
* **Consecuencias Justas y Comprensibles:**
  * Si un tren descarrila por exceso de velocidad en una curva o dos convoyes colisionan por una señal mal planteada, la causa debe ser transparente y rastreable. El error en LeTrain es didáctico: enseña al jugador a ser mejor ingeniero.

---

## 4. La Filosofía de la Automatización: El Jugador como Programador

LeTrain eleva la logística dotando al jugador de herramientas de computación sobre las vías.

* **Un Lenguaje Propio (DSL con ANTLR):**
  * En lugar de depender únicamente de interfaces gráficas o menús contextuales, LeTrain ofrece una consola y un lenguaje declarativo para definir rutas, itinerarios, velocidades y automatismos de autopilotaje.
* **Determinismo y Escenarios en Texto Plano:**
  * Una semilla (*seed*) y una receta de comandos (`.ltr`) reproducen exactamente el mismo universo ferroviario en cualquier máquina, sistema operativo o momento histórico.
  * Los escenarios no requieren volcados binarios opacos: son textos legibles por humanos, versionables en Git y validables mediante herramientas de línea de comandos como `letrain-check`.
* **Libertad para Experimentar sin Miedo:**
  * El sistema de `Record / Undo / Redo` y las instantáneas (*snapshots*) en caliente existen para invitar a la audacia. El jugador debe poder probar una infraestructura arriesgada y retroceder en el tiempo si el resultado no le satisface.

---

## 5. El Contrato de Calidad y Robustez

Un sistema que aspira a perdurar debe estar construido con estándares profesionales innegociables.

* **Cero Tolerancia a los Errores Fantasma:**
  * Las colisiones, bloqueos o pérdidas de carga deben ser siempre consecuencia lógica de las reglas del juego, nunca de condiciones de carrera, errores de concurrencia o estado mutado incontrolado.
* **Arquitectura Desacoplada (MVP):**
  * El Modelo (`Model`) es el guardián de la verdad y desconoce por completo a los Presentadores y a las Vistas. Ningún detalle de UI o de renderizado (Lanterna, LibGDX, OpenGL) debe contaminar el motor de juego (`core`).
* **La Red de Seguridad del Test:**
  * Todo comportamiento clave, regla física, parser de comandos o cálculo de economía debe estar respaldado por pruebas unitarias e integrales (`JUnit 5`, `Mockito`). Si una modificación rompe la cobertura o introduce regresiones, no está lista.

---

## 6. Mandato para Futuros Custodios y Colaboradores

Si estás leyendo este documento con la intención de continuar, mantener o evolucionar LeTrain en ausencia de sus creadores originales, asume este compromiso:

1. **La Prueba de la Caja de Cartón:** Antes de introducir una nueva mecánica, pregúntate: *¿Le estamos dando al jugador una herramienta para inventar o un juguete que juega solo? ¿Es un mecanismo honesto o de cartón piedra?*
2. **Preserva la Identidad:** Si una función pertenece a un simulador hiperrealista barroco o a un clicker casual, no pertenece a LeTrain. Respeta la sobriedad y la dignidad del puesto de mando.
3. **Respeta la Simetría:** Mantén la paridad estricta entre el cliente de terminal (2D) y el cliente gráfico (3D). Si una función no puede expresarse con elegancia en ambas dimensiones, replantéala.
4. **Garantiza la Soberanía del Teclado (*Keyboard-First*):** Jamás conviertas a LeTrain en un juego dependiente del ratón. El 100% de las acciones, navegación y comandos deben poder ejecutarse fluidamente desde el teclado con teclas Vim y consola. El ratón puede ser una comodidad opcional, pero nunca un requisito.
5. **Prioriza la Simplicidad sobre la Astucia:** Prefiere código transparente, mantenible y modular antes que optimizaciones prematuras o abstracciones vacías.
6. **Registra las Decisiones de Arquitectura:** Todo cambio significativo debe explicarse y documentarse mediante un ADR (`docs/developer/adr/`). La arquitectura es el mapa que permite no perderse en el futuro.
7. **Deja el Taller Limpio:** Borra las ramas tras integrarlas, no dejes volcados temporales en la raíz, mantén los índices de clases actualizados y haz que el siguiente desarrollador encuentre un repositorio en el que dé gusto trabajar.
