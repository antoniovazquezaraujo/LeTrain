# ADR-009: Editor de Itinerarios (DSL)

## Estado: IMPLEMENTADO

> Sustituye al diseño original de UI gráfica (VisUI/LibGDX).  
> El editor gráfico fue abandonado por inviabilidad de navegación por teclado en scene2d.

---

## DSL de Itinerarios

El editor de itinerarios se implementa como un lenguaje de dominio específico (DSL)
integrado en el IDE existente (`p` → LT-IDE). Los comandos se ejecutan al pulsar **APPLY**.

### Sintaxis

```
// ── Nombrado ──
station 1 set name "Madrid";
sensor 34 set name "Entrada Norte";
train 1 set name "Expreso";

// ── Crear itinerario ──
create itinerary "Ruta 1" {
    add station "Madrid" se, arrival 9:00, load, departure 9:30
    add sensor "Entrada Norte" n, reverse
    add station "Barcelona" n, unload
}

// ── Asignar y activar ──
assign itinerary "Ruta 1" to train "Expreso";
train "Expreso" set autopilot true;
```

> **Forma antigua rechazada:** la versión sin comas
> (`add station "Madrid" S LOAD REVERSE`) ya no parsea. Las comas son obligatorias entre los ítems
> del plan y el orden es `arrival`, acciones, `departure`. La referencia viva es
> [`docs/user/grammar.md`](../../user/grammar.md) y la quick reference del editor
> (`GrammarReference`).

### Reglas

- Un itinerario requiere al menos 2 waypoints (estaciones o sensores)
- Las estaciones/sensores/trenes se referencian por **nombre** o por **ID numérico**
- Cada waypoint acepta **múltiples acciones separadas por comas** (`load, reverse`)
- La dirección de entrada es opcional y en minúsculas (`n`, `s`, `e`, `w`, `ne`, `nw`, `se`, `sw`)
- Acciones: `load`, `unload`, `reverse`/`invert`, `stop`, `park`, `wait N`, `speed N`,
  `couple`/`uncouple`, `fork …`, `stop at …`, más los atributos `arrival`/`departure`
  (lista completa en `grammar.md`)
- En modo DRIVE, `↑`/`↓` desactivan el autopilot; `a` lo reactiva

### Implementación

- Gramática ANTLR: original en `LeTrainProgram.g4` (refactorizada posteriormente en `ScriptLogicParser.g4` y `PlayerCommandsParser.g4`)
- Visitor: `CommandManager.java` — usa `ItineraryImpl` y `WaypointImpl` directamente
- La ejecución ocurre en `AutomationEngine.setProgram()` al pulsar APPLY
- El programa de texto es la **fuente de verdad**: cada APPLY borra y recrea el estado

### Cambios en el modelo

- `Sensor` y `Train` ahora tienen campo `name` (Station ya lo tenía)
- `Model` expone `findStationByName()`, `findSensorByName()`, `findTrainByName()`
