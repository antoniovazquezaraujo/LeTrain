# ADR-022: Tiempo de Juego (Reloj, Día/Noche y Horarios)

## Estado: PROPUESTO — fase 0 implementada (reloj, HUD y comando `time set`); fase 2a implementada (gramática y modelo de horarios); fase 2b implementada (retención, `park` y métrica de puntualidad en core; el HUD llega en 2c)

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
  desfase (puede ser negativo = adelantado). **No retiene ni regula la velocidad**: si llega antes,
  espera a su `departure` (o continúa con sus acciones si no lo tiene).
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
- **Convención de signo (implementada)**: `+` = tarde, `−` = adelantado (`+2` = dos minutos tarde,
  `−1` = un minuto adelantado); el cero exacto se imprime sin signo (`0 min`, no `+0 min`).
- **Media y máximo (implementados)** se calculan sobre **todas las medidas** (cada llegada y cada
  salida cuenta una): el *actual* es la última medida registrada (la salida de la última parada; o
  su llegada si aún no tiene salida). El **máximo es con signo**: es el mayor valor, de modo que en
  un servicio todo adelantado imprime el menos adelantado (`Max: −1 min`).
- `arrivalDelta` se mide al entrar en el waypoint; `departureDelta`, cuando termina la retención
  (a la hora programada, o antes si llegó tarde: sale de inmediato y el desfase es positivo).
- Se muestra en **`info train N`** (detalle: una línea por parada más actual/media/máximo) y, en
  la fase 2c, en el **HUD** con un simple número con signo referido al tren seleccionado/en
  conducción; si el itinerario no tiene horas, o aún no se ha medido ninguna parada, no se muestra
  nada. La historia vive en memoria (no viaja en el guardado): al cargar, el servicio se reanuda y
  la vuelve a medir.

Compatibilidad (**decidida**): la sintaxis nueva es **estricta en todos los puntos de entrada**
(consola, editor, escenarios, partidas guardadas, journals y `letrain-check`): las acciones sin
comas y los atributos fuera de orden son errores con diagnóstico. Al ser una beta **sin compromiso
de compatibilidad**, el texto viejo **no se migra**: los escenarios, programas y journals escritos
con la sintaxis anterior (`add station 2 reverse unload`, `SPEED 0 WAIT 3 SPEED 3`) quedan inválidos
y hay que reescribir sus itinerarios a mano; los ejemplos del repo se migran en esta entrega
(`docs/user/grammar*.md`, `AutoPilotIntegrationTest`) y el exportador ya emite la sintaxis nueva
porque el journal vivía en memoria. Lo único que se conserva es el **formato de guardado**: un JSON
viejo sin los campos `arrival`/`departure` carga con horas vacías
(`core/src/test/resources/bucle.json` se conserva como test de ese caso). `WAIT n` conserva su
semántica (segundos de simulación) y los itinerarios sin horas siguen funcionando igual.

Implementación de la fase 2a: las horas viven en el `Waypoint` (`arrival`/`departure`, un
`LocalTime` opcional cada una), se validan en la gramática (token `TIME`, comas y orden
obligatorios) y sobreviven a `GameSaveService` (JSON `HH:mm`) y al export/import de escenarios. La
semántica de secuencia y estancia vive en `letrain.itinerary.Timetable` (`resolveAfter`/
`dwellMinutes`, con rollover de medianoche).

Implementación de la fase 2b (esta entrega): el autopilot **retiene** en el waypoint hasta su
`departure` con un despertar determinista por ticks (`GameClock.ticksUntil` + `SimulationScheduler`;
si el reloj retrocede, se reprograma). La secuencia se lleva con un **cursor** de minuto absoluto
por servicio: la primera hora se resuelve con `Timetable.resolveNearest` y las siguientes con
`Timetable.resolveAfter` (rollover al volver al primer waypoint; en el empate exacto a ±12 h se
mantiene la ocurrencia del día actual). Al llegar a un waypoint con `departure` el tren **frena** y,
si va a esperar, el autopilot pasa a `WAITING` (una orden de velocidad durante la espera queda
diferida y se restaura en la salida). La `departure` **arranca el motor** de todo el tren, lo que
cierra el ciclo del `park`; `park` es una acción nueva (`WaypointCommand.Kind.PARK`) que frena,
apaga el motor y **mantiene el autopilot** (frente a `stop`, que lo desactiva). Un `park` **sin
`departure` posterior** deja al tren aparcado con el motor apagado: el cursor del plan avanza como
en cualquier waypoint, pero el tren no vuelve a moverse hasta una salida programada (que arranca el
motor) o una orden manual; es un fin de servicio, no una espera activa. Los comandos diferidos
(`park`, `load`/`unload`) se reanudan también al **contacto con el tope de vía**, donde la parada
llega por `emergencyStop` dentro del guard de reentrada y no dispara `onSpeedChanged`; sin ese
camino el plan quedaría clavado en cocheras/terminales. La seguridad manda: la retención no toca
cantones; si el bloque siguiente está ocupado, el tren espera y el retraso se refleja en la
siguiente medida. La métrica vive en `letrain.itinerary.Punctuality` y se expone en `info train N`.
**Al cargar**, el cursor de secuencia no viaja en el guardado: la primera hora se re-resuelve con
`resolveNearest` (una salida ya pasada libera de inmediato) y el replay del programa reinicia el
servicio igualmente. Tests de referencia: `RetentionParkMetricsTest` (reloj con `time set` + ticks),
`PunctualityTest` y `TimetableTest`.

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

> **Nota:** `park` ya está implementado (fase 2b); los comentarios `//` son ilustrativos (el DSL no
> admite comentarios todavía).

- **Cómo se inicia cada mañana**: el tren pasa la noche en cocheras (el último y el primer
  waypoint son el mismo sitio) y a las 06:00 la `departure` del primer waypoint lo libera. La
  salida programada debe **arrancar el motor**: el `park` lo deja apagado de forma explícita y un
  tren así no se mueve con una orden de velocidad.
- **`park` (nuevo) frente a `stop` (actual)**: `stop` frena y **desactiva el autopilot** (fin de
  servicio, paso a manual), así que no sirve para una jornada que se repite; `park` frena y apaga
  el motor **manteniendo el autopilot** a la espera de la próxima salida programada. **Decidido**:
  `park` se añade como acción nueva y `stop` conserva su significado actual.
- **Cambios de sentido (push-pull)**: la composición del ejemplo lleva **una locomotora en cada
  extremo** (el jugador las paga), así que el `reverse` de los terminales basta: la nueva cabeza
  pasa a tirar. Un jugador "pro" puede programar el *run-around* (desenganchar, mover la locomotora
  a la otra vía, volver a enganchar e invertir) **como acciones del waypoint** con las órdenes de
  tren (`uncouple`/`couple`/`stop at`…, ver *Maniobras en el itinerario*), o desde scripts.
- **Faros al invertir**: deben seguir al **frente físico** (`Train.getPhysicalFront()`): en push-pull
  se apagan los de la cola y se encienden los de la nueva cabeza; con una sola locomotora siguen
  encendidos aunque miren hacia los vagones (issue #618).
- **El bucle es el comportamiento por defecto**: el autopilot vuelve siempre al primer waypoint
  (no se añade un atributo `loop` ni `once` por ahora).
- **Sin azúcar `repeat`**: cada vuelta lleva su propio horario (no son las mismas paradas a las
  mismas horas), así que los waypoints se escriben a mano, aunque sean más líneas.

#### Maniobras en el itinerario

Las maniobras "pro" (run-around, apartarse, mover la locomotora sola) se escriben como **acciones
del waypoint**, con las mismas órdenes de tren que los scripts. El autopilot las ejecuta **en
orden** al llegar a la parada; las de movimiento (`stop at …`) son misiones que deben completarse
antes de pasar a la siguiente acción, y el `departure` libera cuando la maniobra ha terminado (si
tarda más, el tren sale tarde y se mide).

```letrain
add station "B" arrival 06:27,
               uncouple forward 1,
               stop at sensor 5 speed 2,     // entra en el bucle
               reverse,                      // el cambio de sentido lo escribe el autor
               fork 3 set curved,            // si hace falta, fuerza el desvío de vuelta
               stop at sensor 6 speed 2,     // vuelve por el otro lado del tren
               couple forward 1,
               reverse,                      // queda mirando hacia la salida
               departure 06:45;
```

- **Los cambios de sentido son explícitos**: cada `reverse` va escrito entre tramos. Dentro del
  itinerario, `stop at` **no** auto-invierte: si falta un `reverse`, no hay ruta desde el sentido
  actual y se avisa. La auto-inversión (opción B de la issue #619) es para órdenes sueltas de
  scripts/consola, donde no hay coreografía escrita.
- **Agujas**: el autopilot ya orienta los desvíos a lo largo de la ruta que calcula
  (`ensureForkRoute`); además, el waypoint puede llevar acciones de fork (`fork 3 set curved`,
  `fork 3 flip`) para forzar un camino o dejarlo preparado.
- **Las órdenes sueltas no pisan el plan**: si el tren está cumpliendo un itinerario, una orden
  suelta de consola o script (`stop at …`, `invert`, etc.) se **rechaza con aviso** (consola y
  log: "está en itinerario; quítale el autopilot o escríbela en el itinerario"). Nada de pausar y
  reanudar en silencio. Para maniobras manuales: `train N set autopilot false;`.
- La maniobra **no se recorta**: el horario es plan, la maniobra es trabajo; si no da tiempo, el
  tren sale tarde y el desfase se mide.

#### Cruces en vía única: cantones (seguridad) y horario (plan)

Un **cantón** es el tramo entre **nodos**: bifurcaciones (`ForkRailTrack`) y extremos/empalmes
irregulares (`getConnections().size() != 2`). Las **estaciones y sensores no parten el cantón** por
sí solos: un apeadero en mitad de una línea recta vive dentro del mismo cantón.

Consecuencias:

- **Apartadero de verdad** (dos desvíos con su vía de apartado): la vía principal y la de apartado
  son cantones distintos entre los desvíos, así que dos trenes pueden estar a la vez en el tramo
  (uno en la principal, otro en el apartado) y el que espera lo hace **dentro de su cantón: rueda
  hasta el final y frena para quedar justo delante del fork**. La espera es corta: lo que tarda el
  otro en recorrer el tramo.
- **Línea A—B sin nodos intermedios**: todo el trayecto es **un solo cantón**; el primero que lo
  reclama entra (`BlockManager.tryLock` da un dueño por cantón) y el otro espera **al final de su
  cantón, justo delante de la frontera**, el cruce completo. No puede "avanzar hasta el apartadero"
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
de cada clase. **Los pasajeros tienen prioridad siempre** (no solo cuando van con retraso); lo único
que puede superarla es la **antiinanición**. Dos cautelas:

- **Antiinanición**: "siempre primero" puede dejar a un mercancías esperando sin fin. Un tren que
  lleve esperando más de X minutos de juego pasa en la siguiente liberación, **por delante de
  cualquier clase** (pasajeros incluidos). Queda fijar X (o, alternativamente, un cupo de N cesiones
  consecutivas por clase).
- **La prioridad no expulsa**: si el cantón ya está ocupado, el prioritario espera a que se libere;
  decide *quién espera cuando hay cola*, no crea vía.

La clase vive en el tren (todos `DEFAULT` mientras no haya pasajeros) y conviene mostrarla en
`info train` junto al tiempo de espera.

**Los apartaderos se usan solos**: el jugador construye la infraestructura (dos desvíos con su vía de
apartado) y el sistema la aprovecha automáticamente: un tren que no puede continuar (cantón ocupado)
**se aparta a la vía libre** —el cantón paralelo entre los mismos nodos, que
`tryAlternativeSegment` ya sabe detectar— y cede la directa al que pasa. El que espera **rueda hasta
el final de su cantón y frena con la curva de frenado que le deja parado justo delante del fork**
(nunca un frenazo en seco); si no le da tiempo a parar, **entra sin permiso** (no hay muros
artificiales), y si el tren es más largo que el apartadero, sobresale: cuanto más grandes los
desvíos, mejor. El itinerario **no** necesita
sensores ni waypoints en los apartaderos: los cruces se resuelven con las horas de las estaciones y
la seguridad. Un punto de control (sensor) en un apartadero es opcional, solo para medir el paso o
forzar una retención ahí.

El horario es la **capa de plan** encima de la seguridad: decide **quién espera** y el
`departure` del apartadero sincroniza el cruce ("no salgas
antes de las X"). Regla de oro: **el horario nunca anula la seguridad**; si el cantón está ocupado,
se espera y el retraso se mide.

Queda abierto: la **prioridad** cuando el plan se cruza con imprevistos (retrasos, trenes manuales)
y si algún día conviene una negociación automática de encuentros (elegir apartadero y prioridad sin
horario) en lugar de confiar en el plan.

Punto abierto: fijar el parámetro de la antiinanición (X minutos de espera o N cesiones
consecutivas) cuando existan los trenes de pasajeros.

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

- Fase 2 diseñada (ver *Horarios* y *Cruces en vía única*): solo queda fijar el parámetro de la
  antiinanición (X minutos de espera o N cesiones consecutivas) cuando existan los trenes de
  pasajeros.

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
