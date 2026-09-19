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

1. **Fecha de juego**: `GameTime` expone `dayOfYear` (día 1 = 1 de enero) o una estación derivada,
   que viaja al `soundscape` junto a la hora.
2. **Sección `[seasons]` en el estilo**: tramos por día del año (o por estación) con las
   intensidades base y su variabilidad por variable: `rain`, `wind`, `storm` y **`snow`** (nueva),
   más una `temperature` opcional. Ejemplo:
   `1/1–21/3  rain=0.8 snow=0.4 wind=0.5`.
3. **Generador de clima determinista**: semilla por partida y día de juego; el clima evoluciona en
   **frentes suaves** a lo largo del día (no ruido por tick), se puede guardar en la partida y
   produce el estado que consumen motor de audio y vistas (lluvia/nieve en pantalla).
4. **Nieve**: nueva variable en el estado, en los presets del estilo y en el motor; assets
   sintetizables (viento + ambiente apagado) y su parte visual.
5. **Fauna estacional**: overrides de `[presence]` por estación o sección `[seasonal]` por sonido;
   la elección de especies (p. ej. cuervos en invierno, estorninos en verano) usa las familias y
   tomas de ADR-024, no ficheros sueltos elegidos a mano.

## Consecuencias

- Positivas: coherencia clima-audio-visual; estaciones con carácter propio; replay reproducible;
  los escenarios pueden forzar clima sin romper el modelo.
- Costes y riesgos: fecha en el reloj, tabla y generador nuevos; `snow` (audio y visual) desde
  cero; decidir granularidad (¿un valor por día o evolución por franjas?), transiciones entre
  tramos estacionales y persistencia del clima en el guardado.
- Dependencias: ADR-022 (reloj), ADR-024 (tomas), ADR-025 (entrada de estado), ADR-026 (dominios).

## Alternativas consideradas

- **Clima aleatorio por tick**: descartado; parpadea, no es reproducible y no se siente como clima.
- **Elegir clima a mano por escenario**: válido como override (ADR-020), pero no cubre juego libre.
- **Estaciones solo visuales**: descartado; se perdería la parte sonora y la fauna.

## Plan por fases

1. `dayOfYear` + `[seasons]` + generador determinista con **demo en el GUI/tour** para oírlo antes
   de tocar el juego.
2. Conectar el clima al juego y a las vistas (lluvia/nieve, cielo).
3. Fauna estacional y selección de especies sobre ADR-024.

## Preguntas abiertas

- ¿`dayOfYear` real (1 de enero) o estaciones genéricas (primavera/verano/otoño/invierno)?
- Granularidad: ¿un valor de clima por día, o evolución por franjas horarias?
- ¿Dónde vive el generador: `core` (conoce el reloj) o el `soundscape` (aislado)?
- ¿La nieve afecta a física/economía en fase 1 o es solo ambiental?
- ¿Cómo se guarda el clima en la partida y cómo interactúa con los overrides de escenario?
