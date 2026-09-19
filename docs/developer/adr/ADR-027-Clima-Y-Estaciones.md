# ADR-027: Clima y estaciones (fecha de juego, tablas estacionales y clima determinista)

## Estado: PROPUESTO (borrador para revisión)

## Contexto

- [[ADR-022-Game-Time]] tiene un reloj de juego con **día, hora y minuto** (`GameTime`,
  `isNight()`, `getDayNightRatio()`), pero **no hay fecha ni estación**: el día es un contador.
- El `soundscape` recibe intensidades `rain`, `wind` y `storm` como entrada y el estilo declara
  presets en `[climate]`. Hoy esos valores los fija el jugador/reproductor de pruebas; **no hay
  generador de clima** ni variable `snow`.
- La fauna se regula con `[presence]` por franja horaria (7 bandas). **No hay estacionalidad**, y
  la variedad de tomas por sonido está pendiente de [[ADR-024-Soundscape-Material-Model]].
- [[ADR-020-Command-Journal-Scenarios]] exige determinismo y replay reproducible: el clima no
  puede sortearse de nuevo en cada partida ni parpadear cada tick.

## Decisión (propuesta)

1. **Fecha de juego**: `GameTime` expone el **día del año** (numérico), que viaja al `soundscape`
   junto a la hora. No se imponen estaciones fijas.
2. **Calendario climático configurable (`[seasons]`), en el estilo**: el usuario define **los
   tramos de días que quiera** (no estaciones estándar) y les asigna la probabilidad/intensidad de
   cada fenómeno, para que cada país o escenario tenga su calendario. **Formato en texto para
   evitar la ambigüedad día/mes** (mes inglés de 3 letras + día numérico, sin años):

   ```
   [seasons]
   Jan-01 to Apr-21   rain=0.8 wind=0.5
   Apr-22 to Jun-20   rain=0.4 wind=0.3
   Jun-21 to Sep-21   rain=0.1 wind=0.2
   Sep-22 to Dec-31   rain=0.5 wind=0.4
   ```

   Reglas de parseo: mes `Jan`…`Dec` insensible a mayúsculas; día opcional (`Jun to Sep` = meses
   completos); rangos que cruzan el año permitidos (`Dec-01 to Feb-28`), comparando en círculo;
   **solapes = error de carga**; los días sin cubrir usan una línea `default` o quedan sin
   fenómenos.
3. **Generador de clima determinista por horas**: cada **hora de juego** se evalúan las
   probabilidades del tramo para decidir si cada fenómeno (`rain`, `wind`, `storm`) arranca o se
   detiene,
   con **histéresis y una duración mínima de una hora de juego** (evita el parpadeo) y rampas
   suaves de entrada/salida.
   Semilla por partida: misma semilla ⇒ misma secuencia (replay de ADR-020). El estado generado es
   el que consumen motor de audio y vistas. **El generador vive en `core`**, que es quien tiene el
   reloj, el guardado y el replay; el `soundscape` y las vistas solo lo consumen.
4. **Nieve**: queda **fuera de la fase 1** (se añadirá más adelante, p. ej. con contenido navideño).
   La variable existirá en el modelo para entonces.
5. **Persistencia y configuración**: el calendario climático vive en el **estilo** (`[seasons]`,
   es configuración de decorado reutilizable); el escenario **`.ltr` puede sobrescribirlo** para
   una situación concreta (fecha inicial, clima forzado); la **semilla y el estado generado** se
   guardan con la partida.
6. **Fauna estacional**: overrides de `[presence]` por tramo del calendario o sección `[seasonal]`
   por sonido; la elección de especies (p. ej. cuervos en invierno, estorninos en verano) usa las
   familias y tomas de ADR-024, no ficheros sueltos elegidos a mano.
7. **Dónde viven los estilos y cómo se eligen**: los estilos son ficheros `*.sound`; el módulo
   trae un **default incluido** (`styles/valle-norte.sound`). El juego elige estilo por
   **configuración**, aprovechando el mecanismo que ya existe: `letrain.cfg` y, por tanto, la
   sección `configuration` del escenario `.ltr` (que ya gana al fichero local), con una clave tipo
   `soundscape.style` (nombre resuelto en una carpeta `styles/` o ruta de fichero). Los estilos de
   usuario/comunidad se dejan en esa carpeta `styles/`, resuelta como `letrain.cfg`, y se añaden
   sin recompilar. La semilla y la fecha inicial del clima pueden ir en la misma configuración
   para escenarios que fuerzan una situación.
8. **Materiales propios en estilos de usuario**: hoy `SampleResolver` solo resuelve dentro del
   classpath del módulo (`sounds/`), así que un estilo de usuario no puede traer sus propios
   audios. Se propone resolver los materiales por este orden: (1) **relativo a la carpeta del
   estilo**, (2) **carpeta de sonidos de usuario** (configurable, p. ej. `sounds/` junto a
   `letrain.cfg`), (3) **classpath** del módulo. Así un mod es autocontenido
   (`mimod/mi.sound` + `mimod/sounds/*.wav`). Formato canónico documentado (WAV mono 44.1 kHz
   16 bits) y validable con `tools/audit_samples.py`.
9. **Paquete de mod (mapa + estilo + sonidos)**: el escenario se guarda/exporta con el editor de
   escenarios del juego (`.ltr`); su sección `configuration` apunta al estilo (`soundscape.style`)
   y puede fijar semilla/fecha. El paquete es una carpeta autocontenida:

   ```
   mi-mapa/
   ├── mi-mapa.ltr          # escenario: semilla + configuration (soundscape.style=mi-estilo.sound)
   ├── mi-estilo.sound      # estilo: zonas, presencia, [seasons], [climate]…
   ├── sounds/              # audios propios (WAV mono 44.1 kHz 16 bits)
   └── CREDITS.md           # autoría y licencias de los audios
   ```

   Se distribuye como ZIP y se descomprime en la carpeta de escenarios/mods; la resolución
   relativa del punto 8 lo hace funcionar sin recompilar. Validación: `letrain-check` para el
   `.ltr` y `tools/audit_samples.py` para el audio. Futuro: comando "Exportar escenario con
   sonidos…" que empaquete todo.

## Consecuencias

- Positivas: coherencia clima-audio-visual; estaciones con carácter propio; replay reproducible;
  los escenarios pueden forzar clima sin romper el modelo.
- Costes y riesgos: fecha en el reloj, tabla y generador nuevos; transiciones entre tramos del
  calendario y persistencia del clima en el guardado. La nieve queda fuera de la fase 1.
- Dependencias: ADR-022 (reloj), ADR-024 (tomas), ADR-025 (entrada de estado), ADR-026 (dominios).

## Alternativas consideradas

- **Clima aleatorio por tick**: descartado; parpadea, no es reproducible y no se siente como clima.
- **Elegir clima a mano por escenario**: válido como override (ADR-020), pero no cubre juego libre.
- **Estaciones solo visuales**: descartado; se perdería la parte sonora y la fauna.

## Plan por fases

1. `dayOfYear` + calendario `[seasons]` configurable + generador horario determinista, con
   **demo en el GUI/tour** para oírlo antes de tocar el juego.
2. Conectar el clima al juego y a las vistas (lluvia, cielo).
3. Fauna estacional y selección de especies sobre ADR-024.
4. Nieve (modelo, audio y visual), más adelante.

## Preguntas abiertas

- Nombre exacto de la clave de configuración (`soundscape.style`) y de la carpeta de estilos de
  usuario (¿`styles/` junto a `letrain.cfg`?).
- ¿El calendario se sobrescribe solo desde el `.ltr` o el estilo puede declarar un override
  pensado para un escenario?
- ¿La carpeta de sonidos de usuario es fija y configurable, o basta con que sea relativa al
  estilo?


