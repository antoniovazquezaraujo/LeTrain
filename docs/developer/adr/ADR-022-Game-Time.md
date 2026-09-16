# ADR-022: Tiempo de Juego (Reloj, Día/Noche y Horarios)

## Estado: PROPUESTO (borrador para revisión conjunta con el equipo `agy`)

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

## Decisión (propuesta)

1. **El tiempo de juego se deriva de ticks lógicos**, nunca del reloj de pared. Debe ser
   determinista, reproducible en el replay del diario de comandos (ADR-020) y testeable headless.
2. Nuevo servicio **`GameClock`** (interfaz + implementación) en `letrain.time`:
   - convierte ticks ↔ hora de juego;
   - expone `isNight()` para las vistas;
   - notifica cambios de hora/día mediante listeners (event-driven, sin polling en `tick()`).
3. **Escala configurable** en `letrain.cfg`, en segundos de juego por tick. Referencia: un día de
   24 h (86.400 s de juego) tarda 72 min reales con escala 1, 36 min con escala 2 y 72 s con
   escala 60. Valor inicial propuesto: **1**.
4. **Hora inicial fija** (propuesta: 06:00), día de 24 h. Sin fechas ni estaciones del año en esta
   fase; el modelo queda preparado para añadirlas.
5. **Serialización**: contador `elapsedTicks` (long) en `Model`. Los guardados antiguos cargan con
   el valor por defecto sin romperse.
6. Los **eventos de tiempo** (cambio de hora, día/noche, horarios) se despachan vía
   `SimulationScheduler`; nada de lógica nueva en bucles periódicos.
7. **`WAIT n` se mantiene en segundos de simulación** para no romper escenarios existentes. Los
   horarios se expresan en tiempo de juego: `DEPART hh:mm`, `UNTIL hh:mm` y disparadores
   `on time hh:mm`.
8. **Día/noche es visual** en esta fase (paleta del terminal y luz/faros en 3D); `isNight()` queda
   disponible para un futuro efecto sobre el gameplay (visibilidad, faros obligatorios, etc.).

### Contrato preliminar (a congelar antes de paralelizar)

```java
public interface GameClock {
    long elapsedTicks();

    void tick(); // lo avanza SimulationController en cada tick lógico

    GameTime now(); // record inmutable: día, hora, minuto

    boolean isNight();

    void setScale(int gameSecondsPerTick);

    void addListener(GameClockListener listener);
}
```

## Fases propuestas

| Fase | Alcance | Entregable |
|---|---|---|
| 0 | Reloj de juego: `GameClock`, escala en config, serialización, HUD y comando `time set` | Sin efecto en gameplay; tests deterministas |
| 1 | Día/noche: paleta 2D y luz/faros 3D usando `isNight()` | Visual; coordinar con #480 |
| 2 | Horarios: `DEPART hh:mm` en itinerarios y puntualidad básica | El autopilot espera; se mide el delta |
| 3 | Eventos por hora (`on time`), demanda por franjas e integración con trenes de pasajeros | Engancha con `PassengerTrains_Design.md` |
| 4 | Economía horaria: tarifas por franja, multas por retraso, mantenimiento nocturno | Reglas de negocio |

## Decisiones abiertas (a resolver conjuntamente)

| Decisión | Propuesta de este equipo | Propuesta equipo `agy` |
|---|---|---|
| Origen del tiempo | Ticks lógicos (determinista) | *(pendiente)* |
| Escala por defecto | `time.scale = 1` configurable | *(pendiente)* |
| Hora inicial / modelo | 06:00, día de 24 h, sin fechas | *(pendiente)* |
| Semántica de `WAIT n` | Segundos de simulación (compatibilidad) | *(pendiente)* |
| DSL de horarios | `DEPART hh:mm`, `UNTIL hh:mm`, `on time` | *(pendiente)* |
| Día/noche | Visual en fase 1; `isNight()` para gameplay futuro | *(pendiente)* |
| Eventos | `SimulationScheduler` en tiempo de juego | *(pendiente)* |
| Serialización | `elapsedTicks` con default compatible | *(pendiente)* |
| Puntualidad | Delta entre visitas a la misma estación | *(pendiente)* |

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
