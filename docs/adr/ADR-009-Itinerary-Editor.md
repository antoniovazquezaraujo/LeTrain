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
    add station "Madrid" S LOAD REVERSE
    add sensor "Entrada Norte" N REVERSE
    add station "Barcelona" N UNLOAD
}

// ── Asignar y activar ──
assign itinerary "Ruta 1" to train "Expreso";
train "Expreso" set autopilot true;
```

### Reglas

- Un itinerario requiere al menos 2 waypoints (estaciones o sensores)
- Las estaciones/sensores/trenes se referencian por **nombre** o por **ID numérico**
- Cada waypoint acepta **múltiples acciones** (`LOAD REVERSE`, `PARADA UNLOAD`)
- La dirección de entrada es opcional (`N`, `S`, `E`, `W`, `NE`, `NW`, `SE`, `SW`)
- Acciones disponibles: `LOAD`, `UNLOAD`, `REVERSE`, `WAIT N`, `SPEED N`
- En modo DRIVE, `↑`/`↓` desactivan el autopilot; `a` lo reactiva

### Implementación

- Gramática ANTLR: `LeTrainProgram.g4` (comandos directos añadidos a los triggers existentes)
- Visitor: `CommandManager.java` — usa `ItineraryImpl` y `WaypointImpl` directamente
- La ejecución ocurre en `AutomationEngine.setProgram()` al pulsar APPLY
- El programa de texto es la **fuente de verdad**: cada APPLY borra y recrea el estado

### Cambios en el modelo

- `Sensor` y `Train` ahora tienen campo `name` (Station ya lo tenía)
- `Model` expone `findStationByName()`, `findSensorByName()`, `findTrainByName()`
