# ADR-022: Tiempo de Juego (Reloj, Día/Noche y Horarios)

## Estado: PROPUESTO — convergencia con el equipo `agy` incorporada (pendiente confirmar arranque y dueños)

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

## Convergencia con la propuesta del equipo `agy`

Ambas propuestas coinciden en arquitectura y fases. Se adopta lo mejor de cada una:

| Tema | Open | agy | Convergencia propuesta |
|---|---|---|---|
| Origen del tiempo | Ticks lógicos | Ticks lógicos (se congela en pausa) | Ticks lógicos; la pausa de edición (ADR-020) congela el reloj |
| Servicio | `GameClock` (interfaz + impl) | `SimulationClock` | `GameClock` en `letrain.time`, avanza desde `SimulationController.tick()` |
| Escala | `time.scale` (s de juego/tick), default 1 | `time.dayDurationSeconds` (segundos reales por día de juego), default 1440 (día = 24 min reales) | Adoptar `time.dayDurationSeconds = 1440` (más legible); equivale a 3 s de juego/tick |
| Hora inicial | 06:00 | 08:00, Día 1 | 08:00, Día 1 (arranque de jornada) |
| Interpolación visual | `isNight()` | `isNight()` + `getDayNightRatio()` (0..1) | Ambas: `isNight()` para lógica y `getDayNightRatio()` para luces/cielo |
| Eventos | Listeners de hora/día + `SimulationScheduler` | Uso directo desde servicios/presenters | Listeners + scheduler; nada de polling en `tick()` |
| Persistencia | `elapsedTicks` (long) | campo `gameTime` opcional en `Model` | Long simple (`elapsedTicks`) y `GameTime` derivado; saves viejos → 08:00 Día 1 |
| `WAIT n` | Sigue en segundos de simulación | No lo redefine | Se mantiene por compatibilidad; horarios con `DEPART`/`UNTIL` |
| DSL horarios | `DEPART hh:mm`, `UNTIL hh:mm`, `on time` | `DEPART AT hh:mm`, `WAIT UNTIL hh:mm`, `AT "07:00" DO`, `EVERY 30m` | Sintaxis final en el PR de gramática; se adopta `EVERY` como aportación |
| Día/noche | Visual en fase 1 | Sol/luna/cielo, faros, farolas, tinte 2D | Igual: visual primero, `isNight()` disponible para gameplay futuro |
| Economía | Tarifas, multas, mantenimiento nocturno | Mantenimiento diario, turnos de producción, bonus por puntualidad | Se adopta el detalle de agy en la fase 4 |
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
   3 segundos de juego por tick.
4. **Hora inicial**: 08:00 del Día 1, día de 24 h. Sin fechas ni estaciones del año en esta fase;
   el modelo queda preparado para añadirlas.
5. **Serialización**: contador `elapsedTicks` (long) en `Model`; `GameTime` se deriva. Los guardados
   antiguos cargan con el valor por defecto (08:00, Día 1) sin romperse.
6. Los **eventos de tiempo** (cambio de hora, día/noche, horarios) se despachan vía
   `SimulationScheduler`; nada de lógica nueva en bucles periódicos.
7. **`WAIT n` se mantiene en segundos de simulación** para no romper escenarios existentes. Los
   horarios se expresan en tiempo de juego: `DEPART hh:mm`, `WAIT UNTIL hh:mm` y disparadores
   temporales (`at "HH:mm"` / `every 30m`) en la gramática de scripts.
8. **Día/noche es visual** en esta fase (paleta del terminal y luz/faros en 3D); `isNight()` y
   `getDayNightRatio()` quedan disponibles para un futuro efecto sobre el gameplay.

### Contrato preliminar (a congelar antes de paralelizar)

```java
public interface GameClock {
    long elapsedTicks();

    void tick(); // lo avanza SimulationController en cada tick lógico

    GameTime now(); // record inmutable: día, hora, minuto

    boolean isNight();

    float getDayNightRatio(); // 0.0 = pleno día, 1.0 = noche cerrada

    void setDayDurationSeconds(int seconds);

    void addListener(GameClockListener listener);
}
```

## Fases propuestas

| Fase | Alcance | Entregable |
|---|---|---|
| 0 | Reloj de juego: `GameClock`, `time.dayDurationSeconds`, serialización, reloj en HUD 2D/3D y comando `time set` | Sin efecto en gameplay; tests deterministas |
| 1 | Día/noche: paleta 2D, sol/luna/cielo, luz ambiental, faros y farolas en 3D usando `isNight()`/`getDayNightRatio()` | Visual; coordinar con #480 |
| 2 | Horarios: `DEPART hh:mm` / `WAIT UNTIL hh:mm` en itinerarios y puntualidad básica; cruces en vía única y apartaderos | El autopilot espera; se mide el delta |
| 3 | Triggers temporales (`at`/`every`) y demanda por franjas; integración con trenes de pasajeros | Engancha con `PassengerTrains_Design.md` |
| 4 | Economía horaria: mantenimiento diario, turnos de producción, tarifas por franja y bonus/multa por puntualidad | Reglas de negocio |

## Decisiones pendientes de confirmar con `agy`

- Nombre final del servicio (`GameClock` vs `SimulationClock`).
- Sintaxis exacta de la gramática (`DEPART hh:mm` vs `DEPART AT hh:mm`; `at "HH:mm"` vs `AT "HH:mm" DO`).
- Reparto de fases y dueño por rama tras congelar el contrato.

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
