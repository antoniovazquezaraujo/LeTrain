# ADR-022: Tiempo de Juego (Reloj, Día/Noche y Horarios)

## Estado: PROPUESTO — fase 0 en implementación (reloj, HUD y comando `time set`)

## Contexto

- El motor avanza por **ticks lógicos** (~20 TPS, 50 ms). `SimulationController.tick()` es la
  autoridad única compartida por los clientes 2D y 3D.
- `SimulationScheduler` ya planifica tareas **en ticks**; `WAIT n` en itinerarios se convierte con
  `TICKS_PER_SECOND = 20`.
- **No existe hora, fecha ni ciclo día/noche.** `lastSaveTime` es reloj de pared y solo se usa para
  los guardados.
- `docs/developer/systems/PassengerTrains_Design.md` basa su mecánica estrella —la **puntualidad**—
  en la varianza temporal entre visitas a la misma estación: sin reloj de juego no es implementable.
- La issue #480 (igualar el pacing 2D/3D) exige que cualquier tiempo lógico dependa de ticks, no de
  frames.
- ADR-013 ya listaba "modo noche y día" como mejora futura.
- ADR-021 y la serialización Jackson exigen que los campos nuevos de `Model` no invaliden las
  partidas existentes.

## Decisiones de diseño adoptadas

Se compararon dos propuestas que coincidían en arquitectura y fases; la tabla resume la opción
elegida en cada punto:

| Tema | Opción A | Opción B | Decisión adoptada |
|---|---|---|---|
| Origen del tiempo | Ticks lógicos | Ticks lógicos (se congela en pausa) | Ticks lógicos; la pausa de edición (ADR-020) congela el reloj |
| Servicio | `GameClock` (interfaz + impl) | `SimulationClock` | `GameClock` en `letrain.time`, avanza desde `SimulationController.tick()` |
| Escala | `time.scale` (s de juego/tick), default 1 | `time.dayDurationSeconds` (segundos reales por día de juego), default 1440 (día = 24 min reales) | Adoptar `time.dayDurationSeconds = 1440` (más legible); equivale a 3 s de juego/tick |
| Hora inicial | 06:00 | 08:00, Día 1 | 08:00, Día 1 (arranque de jornada) |
| Interpolación visual | `isNight()` | `isNight()` + `getDayNightRatio()` (0..1) | Ambas: `isNight()` para lógica y `getDayNightRatio()` para luces/cielo |
| Eventos | Listeners de hora/día + `SimulationScheduler` | Uso directo desde servicios/presenters | Listeners + scheduler; nada de polling en `tick()` |
| Persistencia | `elapsedTicks` (long) | campo `gameTime` opcional en `Model` | Long simple (`elapsedTicks`) y `GameTime` derivado; saves viejos → 08:00 Día 1 |
| `WAIT n` | Sigue en segundos de simulación | No lo redefine | Se mantiene por compatibilidad; horarios con `DEPART`/`UNTIL` |
| DSL horarios | `DEPART hh:mm`, `UNTIL hh:mm`, `on time` | `DEPART AT hh:mm`, `WAIT UNTIL hh:mm`, `AT "07:00" DO`, `EVERY 30m` | **Horas por parada**: `arrival HH:MM` (medida) / `departure HH:MM` (retención) como atributos del waypoint (ver *Horarios*); `EVERY` entra en la fase 3 |
| Día/noche | Visual en fase 1 | Sol/luna/cielo, faros, farolas, tinte 2D | Igual: visual primero, `isNight()` disponible para gameplay futuro |
| Economía | Tarifas, multas, mantenimiento nocturno | Mantenimiento diario, turnos de producción, bonus por puntualidad | Se adopta el detalle de la opción B en la fase 4 |
| Operación | Puntualidad (delta entre visitas) | Rol de regulador, cruces en vía única | Ambos: cruces y apartaderos como juego emergente de los horarios |

## Decisión (propuesta)

1. **El tiempo de juego se deriva de ticks lógicos**, nunca del reloj de pared. Debe ser
   determinista, reproducible en el replay del diario de comandos (ADR-020) y testeable headless.
   La pausa de edición congela el reloj.
2. Nuevo servicio **`GameClock`** (interfaz + implementación) en `letrain.time`:
   - convierte ticks ↔ hora de juego y expone `GameTime` (día, hora, minuto);
   - expone `isNight()` y `getDayNightRatio()` (0.0–1.0) para las vistas;
   - notifica cambios de hora/día mediante listeners (event-driven, sin polling en `tick()`).
3. **Escala configurable** en `letrain.cfg` como `time.dayDurationSeconds` (segundos reales que
   dura un día de juego). Valor inicial: **1440** (un día cada 24 minutos reales), equivalente a
   3 segundos de juego por tick. La escala se fija al crear la partida y no se cambia en caliente
   (ver *Escala temporal* más abajo).
4. **Hora inicial**: 08:00 del Día 1, día de 24 h. Sin fechas ni estaciones del año en esta fase;
   el modelo queda preparado para añadirlas.
5. **Serialización**: contador `elapsedTicks` (long) en `Model`; `GameTime` se deriva. Los guardados
   antiguos cargan con el valor por defecto (08:00, Día 1) sin romperse.
6. Los **eventos de tiempo** (cambio de hora, día/noche, horarios) se despachan vía
   `SimulationScheduler`; nada de lógica nueva en bucles periódicos.
7. **`WAIT n` se mantiene en segundos de simulación** para no romper escenarios existentes. Los
   horarios se expresan en tiempo de juego como atributos del waypoint (`arrival HH:MM` /
   `departure HH:MM`, ver *Horarios*); los disparadores temporales (`at "HH:mm"` / `every 30m`)
   llegan en la fase 3.
8. **Día/noche es visual** en esta fase (paleta del terminal y luz/faros en 3D); `isNight()` y
   `getDayNightRatio()` quedan disponibles para un futuro efecto sobre el gameplay.
9. **Comando de consola del reloj**: `time;` muestra la hora actual (`Día 1 08:00`);
   `time set HH:MM;` fija la hora del día actual **sin diálogo de confirmación** (el cambio ya se
   ve en el reloj del HUD). El salto es determinista y se journaliza, así que el replay reproduce
   la misma hora. Los instantes anteriores al origen (Día 1, 08:00) ruedan al día siguiente; no se
   toca la duración del día (esa escala se fija al crear la partida).
10. **Ciclo solar por latitud** (fase 1): `SolarModel` (core) calcula amanecer/atardecer y
    elevación/acimut del sol a partir del día del año, la hora y la latitud. `getDayNightRatio()`
    se deriva de la elevación con una banda de crepúsculo de 18°, y `world.latitude`
    (`letrain.cfg`, sobreescribible en el `configuration { }` del escenario) fija la latitud del
    mundo (40 por defecto). A latitudes altas emergen sin código extra las noches blancas (el
    ratio no llega a 1 en verano) y el día/noche perpetuos en los polos. La luz direccional del 3D
    sigue al sol (elevación/acimut).

### Escala temporal: qué cambia y qué no (aclaración)

Hay **dos relojes** y no deben confundirse:

- **Física (ticks):** los trenes avanzan 1 celda cada `50 / velocidad` ticks a ~20 TPS reales.
  No depende del reloj de juego: la velocidad aparente (visual) es la misma con cualquier
  `time.dayDurationSeconds`.
- **Reloj de juego (derivado de ticks):** convierte ticks en hora/día. Es el único afectado por
  `time.dayDurationSeconds`; de él cuelgan el clima (ADR-027), el día/noche y, en fases futuras,
  horarios y puntualidad.

Consecuencias de diseño:

1. **`time.dayDurationSeconds` no acelera el juego**: solo cambia lo rápido que avanza el
   calendario. Es un ajuste de **ritmo atmosférico** (que un ciclo día/noche quepa en una sesión),
   no de velocidad de simulación; nadie "juega más rápido" por subirlo. Acelerar trenes y física
   sería otra cosa (multiplicador de simulación) y no se adopta como opción de juego.
2. **La escala se fija al crear la partida y no se cambia en caliente.** Si se cambiara a mitad de
   partida, el mismo trayecto (los mismos ticks) pasaría a durar más o menos horas de juego y los
   horarios guardados dejarían de ser válidos. `GameClock.setDayDurationSeconds` queda reservado
   para la configuración inicial, escenarios y tests, no para menús en juego.
3. **La puntualidad es invariante al ritmo de juego.** Pausa, máquina lenta o un futuro acelerador
   de simulación (solo para pruebas) multiplican a la vez los ticks de física y el reloj, de modo
   que el tiempo de viaje en horas de juego no cambia; solo cambia lo que se espera en tiempo real.
   Los horarios y la puntualidad se calculan sobre ticks y la escala fija de la partida.
4. **La escala física es "de maqueta":** 1 celda ≈ 20 m (largo de una locomotora). Con el día de
   1440 s (compresión 60×), un viaje de 1 km (50 celdas) a velocidad máxima dura 12,5 min de
   juego. En esta fase el HUD no muestra km/h "reales": los km/h de ferrocarril real y el reloj
   comprimido no pueden ser coherentes a la vez, así que la ficción del juego es la escala maqueta.

Referencia rápida (notch 10 = 5 ticks/celda):

| Acción | Física/trenes | Reloj | Viaje en horas de juego |
|---|---|---|---|
| Pausa de edición | se congela | se congela | invariante |
| Acelerador de simulación (futuro, pruebas) | ×N | ×N | invariante |
| Cambiar `dayDurationSeconds` | sin efecto | nueva escala | cambia (por eso se fija por partida) |

### Horarios (fase 2): horas por parada

Cada **waypoint** del itinerario puede llevar la hora de **llegada** y/o de **salida** como
atributos, en formato de 24 h (`H:MM` o `HH:MM`). Las comas son separadores **obligatorios** entre
acciones (una sola forma de escribir, más legible; la referencia del waypoint y su dirección no
llevan coma):

```letrain
create itinerary "cercanías" {
  add station 1 load, departure 9:20;
  add station 2 arrival 10:23, unload, departure 10:30;
  add sensor 5 arrival 10:37
}
```

Semántica:

- **`arrival HH:MM` es medida**: el autopilot apunta la hora real de llegada al waypoint y su
  desfase (puede ser negativo = adelantado). **No retiene**.
- **`departure HH:MM` es retención**: al llegar se ejecutan las acciones del waypoint (`load`,
  `unload`, `wait n`…); si terminan antes de la hora, el tren **espera**; si terminan después,
  **sale tarde** y el desfase de salida queda registrado. La estancia (`dwell`) es, por tanto,
  `departure − arrival` en **tiempo de juego**, invariante al ritmo (`time.dayDurationSeconds`).
- Sin `departure`, el tren sale cuando acaban sus acciones; sin `arrival`, no hay medida de
  llegada. Sin ninguna hora, el waypoint se comporta como hasta ahora.
- En **sensores** las horas funcionan **igual que en una estación** (también pueden retener hasta su
  `departure`); la única diferencia es que no hay carga ni descarga. (Decidido tras revisión: un
  tren puede necesitar esperar en un sensor.)
- Las horas se leen **en secuencia**: si una es menor que la anterior, pertenece al día siguiente
  (`arrival 23:50 departure 00:10`). Un tren con retraso no espera 24 h: si llega después de su
  hora, sale de inmediato y se mide el desfase.
- **Orden de ejecución obligatorio**: `arrival` (si está) va primero, después las acciones en su
  orden de ejecución (`load`, `unload`, `wait n`…) y `departure` (si está) al final. Escribir un
  atributo fuera de ese orden es un error de validación (no se admiten atributos "declarativos"
  en cualquier posición).

Métrica (fase 2, solo medir; la economía horaria es la fase 4):

- Por parada: `arrivalDelta` y `departureDelta` en **minutos de juego**.
- Por tren: retraso actual (última parada), medio y máximo.
- Se muestra en **`info train N`** (detalle) y en el **HUD** con un simple número con signo
  (`+2` = dos minutos tarde, `−1` = adelantado) referido al tren seleccionado/en conducción; si el
  itinerario no tiene horas, no se muestra nada.

Compatibilidad: los itinerarios sin horas siguen funcionando igual si ya usan comas; `WAIT n`
conserva su semántica (segundos de simulación). Al exigir comas entre acciones, los itinerarios
con varias acciones sin comas (`add station 2 reverse unload`) dejan de ser válidos y hay que
migrarlos al implementar la fase 2: ejemplos de `docs/user/grammar*.md`, tests
(`AutoPilotIntegrationTest`), el exportador de escenarios y los escenarios guardados por el
jugador.

#### La jornada completa (ejemplo)

Servicio diario entre dos estaciones, con cocheras al final de la jornada. **El autopilot ya
recorre el itinerario en bucle** (`advanceWaypoint` vuelve al primer waypoint al terminar), así
que una jornada es un itinerario que se repite solo: las horas se leen en secuencia y, al volver
al primer waypoint, su hora pertenece al día siguiente (rollover).

```letrain
create itinerary "cercanías diario" {
  add station "Cocheras" departure 06:00;        // arranque de la jornada
  add station "A" arrival 06:10, departure 06:15;
  add station "B" arrival 06:27, departure 06:30;
  ...                                            // las otras tres idas y vueltas
  add station "Cocheras" arrival 22:50 park;     // fin de jornada: a cocheras
}
assign itinerary "cercanías diario" to train 1;
```

- **Cómo se inicia cada mañana**: el tren pasa la noche en cocheras (el último y el primer
  waypoint son el mismo sitio) y a las 06:00 la `departure` del primer waypoint lo libera. La
  salida programada debe **arrancar el motor**: el `park` lo deja apagado de forma explícita y un
  tren así no se mueve con una orden de velocidad.
- **`park` (nuevo) frente a `stop` (actual)**: `stop` frena y **desactiva el autopilot** (fin de
  servicio, paso a manual), así que no sirve para una jornada que se repite; `park` frena y apaga
  el motor **manteniendo el autopilot** a la espera de la próxima salida programada. **Decidido**:
  `park` se añade como acción nueva y `stop` conserva su significado actual.
- **El bucle es el comportamiento por defecto**: el autopilot vuelve siempre al primer waypoint
  (no se añade un atributo `loop` ni `once` por ahora).
- **Sin azúcar `repeat`**: cada vuelta lleva su propio horario (no son las mismas paradas a las
  mismas horas), así que los waypoints se escriben a mano, aunque sean más líneas.

#### Cruces en vía única: cantones (seguridad) y horario (plan)

Un **cantón** es el tramo entre **nodos**: bifurcaciones (`ForkRailTrack`) y extremos/empalmes
irregulares (`getConnections().size() != 2`). Las **estaciones y sensores no parten el cantón** por
sí solos: un apeadero en mitad de una línea recta vive dentro del mismo cantón.

Consecuencias:

- **Apartadero de verdad** (dos desvíos con su vía de apartado): la vía principal y la de apartado
  son cantones distintos entre los desvíos, así que dos trenes pueden estar a la vez en el tramo
  (uno en la principal, otro en el apartado) y el que espera lo hace **en el desvío**, a la entrada
  del apartadero. La espera es corta: lo que tarda el otro en recorrer el tramo.
- **Línea A—B sin nodos intermedios**: todo el trayecto es **un solo cantón**; el primero que lo
  reclama entra (`BlockManager.tryLock` da un dueño por cantón) y el otro espera en el nodo
  anterior —su estación de origen— el cruce completo. No puede "avanzar hasta el apartadero"
  porque, para los cantones, ese apartadero no delimita nada: es el mismo segmento.

**Hoy (a sustituir por la decisión de abajo)**: cuando el cantón se libera, `Model` avisa a los que
esperan **recorriendo `model.getLocomotives()` en orden**, y el primero de esa lista que está
esperando el cantón reintenta el lock en el acto (`tryLock` es síncrono) y se lo queda. No es
aleatorio —es determinista y reproducible—, pero **no es FIFO por llegada**: `locomotives` es el
registro de locomotoras del mundo en **orden de creación** (o el del guardado, al cargar) y **no se
reordena con el tráfico**, así que gana la locomotora más veterana de las que esperan, aunque haya
llegado más tarde. El que pierde reintenta en la siguiente liberación (no hay inanición, aunque
puede encadenar esperas). Además, antes de esperar el tren intenta `tryAlternativeSegment`: si
existe un cantón paralelo entre los mismos nodos (p. ej. la vía de apartado) y no tiene paradas
pendientes en el bloqueado, lo toma y evita la espera.

**Decidido (a implementar)**: la prioridad debe ser **FIFO por llegada al cantón**, no el orden del
registro de locomotoras. Plan: al empezar a esperar, el tren pide un **turno** monótono
(determinista, sin reloj de pared, mantenido en el `BlockManager`); al liberarse el cantón, `Model`
ordena a los que esperan por ese turno (empate: id de locomotora) y el primero que lo reclama se lo
lleva; el turno se limpia al dejar de esperar. Hoy gana la más veterana; pasar a FIFO es pequeño y
hay `BlockManagerTest`, `BlockReleaseIntegrationTest` y `TrainSafetyManagerTest` para cubrirlo. El
PR de implementación sustituirá el párrafo *Hoy* de arriba por la descripción del FIFO.

**Prioridades (diseño previsto, para cuando lleguen los trenes de pasajeros)**: la cola se ordenará
por la clave **`(clase, turno)`** — clases tipo **pasajeros > mercancías > maniobras**, FIFO dentro
de cada clase — para que un tren de pasajeros pueda adelantar al resto. Dos cautelas:

- **Antiinanición**: "siempre primero" puede dejar a un mercancías esperando sin fin; se resolverá
  con **envejecimiento** (asciende de clase tras X minutos de juego esperando) o con **prioridad por
  retraso** (el de pasajeros solo adelanta si va por encima de un umbral). La política se decidirá
  cuando existan los trenes de pasajeros.
- **La prioridad no expulsa**: si el cantón ya está ocupado, el prioritario espera a que se libere;
  decide *quién espera cuando hay cola*, no crea vía.

La clase vive en el tren (todos `DEFAULT` mientras no haya pasajeros) y conviene mostrarla en
`info train` junto al tiempo de espera.

El horario es la **capa de plan** encima de la seguridad: decide **quién espera y dónde** (en el
apartadero, no en mitad del tramo) y el `departure` del apartadero sincroniza el cruce ("no salgas
antes de las X"). Regla de oro: **el horario nunca anula la seguridad**; si el cantón está ocupado,
se espera y el retraso se mide.

Queda abierto: la **prioridad** cuando el plan se cruza con imprevistos (retrasos, trenes manuales)
y si algún día conviene una negociación automática de encuentros (elegir apartadero y prioridad sin
horario) en lugar de confiar en el plan.

Puntos abiertos (seguimos pensando): si más adelante `arrival` también limitará la velocidad para
no llegar antes de hora, y la política de prioridad de pasajeros (¿siempre, o solo con retraso?) con
su mecanismo antiinanición (envejecimiento o cupo).

### Contrato (implementado en la fase 0)

```java
public interface GameClock {
    int TICKS_PER_SECOND = 20;

    long elapsedTicks();

    void tick(); // lo avanza SimulationController en cada tick lógico

    GameTime now(); // record inmutable: día, hora, minuto

    boolean isNight();

    float getDayNightRatio(); // 0.0 = pleno día, 1.0 = noche cerrada

    void setDayDurationSeconds(int seconds);

    void setTime(GameTime time); // salto determinista (time set, escenarios y tests)

    void addListener(GameClockListener listener);
}
```

## Fases propuestas

| Fase | Alcance | Entregable |
|---|---|---|
| 0 | Reloj de juego: `GameClock`, `time.dayDurationSeconds`, serialización, reloj en HUD 2D/3D y comando `time set` | Sin efecto en gameplay; tests deterministas |
| 1 | Día/noche: paleta 2D, sol/luna/cielo, luz ambiental, faros y farolas en 3D usando `isNight()`/`getDayNightRatio()` | Visual; coordinar con #480 |
| 2 | Horarios: `arrival` / `departure` por parada en los itinerarios y puntualidad básica; cruces en vía única y apartaderos | El autopilot retiene hasta la salida programada; se mide el delta |
| 3 | Triggers temporales (`at`/`every`) y demanda por franjas; integración con trenes de pasajeros | Engancha con `PassengerTrains_Design.md` |
| 4 | Economía horaria: mantenimiento diario, turnos de producción, tarifas por franja y bonus/multa por puntualidad | Reglas de negocio |

## Decisiones pendientes

- Fase 2 en diseño (ver *Horarios*): quedan por decidir dónde mostrar los desfases, la prioridad
  entre trenes cuando el plan se cruza con imprevistos (ver *Cruces en vía única*) y si `arrival`
  limitará la velocidad más adelante.

## Alternativas consideradas

- **Reloj de pared (`System.currentTimeMillis`)**: descartado; rompe el determinismo, el replay y
  la paridad 2D/3D (issue #480).
- **Tiempo basado en frames/visual**: descartado; el 3D interpola y no todos los frames generan
  tick lógico.
- **Calendario con fechas y estaciones**: se aplaza; el modelo de datos debe permitirlo sin
  migración destructiva.
- **Polling de la hora en cada tick**: descartado por la regla event-driven del proyecto; los
  consumidores se suscriben a eventos de tiempo.

## Consecuencias

- Positivas: desbloquea horarios y puntualidad (pasajeros), escenarios con eventos temporales y el
  ciclo día/noche; base compartida de tiempo para un futuro multiplayer (#247).
- Costes y riesgos: compatibilidad de guardados y del replay del journal; coordinación entre ambos
  clientes para que el reloj avance al mismo ritmo; disciplina para no introducir lógica de tiempo
  dentro de `tick()`.
