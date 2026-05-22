# ADR-008: Sistema de Itinerarios con Waypoints y Control Automático

## Estado: PROPUESTA

## Contexto

ADR-004 definió las bases de los itinerarios (secuencia de estaciones + AutoPilot). Tras meses de evolución del sistema de bloques (ADR-005), extracción de managers, y estabilización del link/unlink, es el momento de refinar el diseño con lo aprendido.

## Objetivos

1. **Itinerario expresivo**: waypoints mixtos (estaciones, sensores) con comandos asociados
2. **Pathfinding determinista**: A* sobre el grafo de segmentos, no sobre coordenadas
3. **Modo dual**: cada tren puede estar en modo MANUAL o AUTOMÁTICO
4. **Forks bajo control**: en modo automático, el sistema cambia forks; en manual, el jugador

---

## 1. Estructura del Itinerario

### Waypoint

Un `Waypoint` es un punto de paso en el itinerario. Opcionalmente puede especificar la **dirección de entrada** deseada:

```
Waypoint {
    targetType:  STATION | SENSOR
    targetId:    int
    entryDir:    Dir | null     // dirección por la que debe entrar (ej: SW, N)
    commands:    List<WaypointCommand>
}
```

Si `entryDir` no es null, el pathfinder solo considerará rutas que lleguen al segmento del destino por esa dirección de entrada. Si es null, cualquier entrada es válida.

```
Waypoint {
    targetType: STATION | SENSOR
    targetId:   int
    commands:   List<WaypointCommand>
}
```

### WaypointCommand

Acción a ejecutar al llegar al waypoint:

| Comando | Efecto |
|---------|--------|
| `LOAD` | Cargar mercancía disponible |
| `UNLOAD` | Descargar mercancía |
| `REVERSE` | Invertir marcha (para cambiar de sentido) |
| `WAIT(n)` | Esperar n ticks |
| `SPEED(n)` | Fijar velocidad objetivo a n |
| `NONE` | Solo pasar (sensor de orientación) |

Las acciones de carga y descarga sobre un sensor no tendrán efecto alguno.

### Ejemplo

```
Itinerario Tren 1:
  1. Waypoint(ESTACION, 3) → [LOAD]
  2. Waypoint(SENSOR, 7)   → []           // forzar paso por aquí
  3. Waypoint(SENSOR, 2)   → [REVERSE]    // invertir marcha
  4. Waypoint(ESTACION, 5) → [UNLOAD]
```

---

## 2. Pathfinding (A* sobre segmentos)

### Grafo de navegación

Los nodos son `Segment` (definidos en ADR-003/ADR-005). Las aristas son las conexiones entre segmentos vía `PathStep` (fork nodes).

```
Nodo = Segment
Arista = (Segment A, Segment B) si comparten un RailNode (fork)
```

### Algoritmo A*

- **Heurística h(n)**: distancia Manhattan desde el nodo de salida del segmento actual hasta el nodo de entrada del segmento destino, más la distancia desde ese nodo hasta el track concreto del destino (estación/sensor)
- **Coste de arista g(n)**: número de `RailTrack`s que contiene el segmento (longitud real)
- **Coste inicial g(0)**: distancia desde el track actual del tren hasta el nodo de salida de su segmento
- **Restricción de entrada**: si el waypoint destino tiene `entryDir`, solo se aceptan rutas cuyo último segmento tenga conexión física en esa dirección
- **Resultado**: lista ordenada de `Segment` desde el actual hasta el destino

### Separación estricta de responsabilidades

El pathfinder es una **función pura**. No controla velocidad, no escucha eventos, no ejecuta comandos, no sabe de trenes:

```
Pathfinder.find(fromSegment, toSegment, entryDir?) → List<Segment>
```

El `AutoPilot` es el que **ejecuta**: recibe la ruta del pathfinder y se encarga de:
- Seguir la secuencia de segmentos
- Controlar velocidad y frenado
- Cambiar forks necesarios
- Detectar llegada al waypoint
- Ejecutar comandos del waypoint (LOAD, UNLOAD, REVERSE...)

### Tramos independientes (REVERSE)

El pathfinder **nunca calcula el itinerario completo de golpe**. Siempre lo hace entre waypoints consecutivos:

**En creación** (modo edición):
- Validar que cada par `waypoint[i] → waypoint[i+1]` es alcanzable
- Si algún par falla → el itinerario no se puede crear

**En ejecución** (modo AutoPilot):
- Calcular ruta al waypoint actual → recorrerla → llegar → siguiente waypoint
- El pathfinder se llama de nuevo para cada tramo (la topología pudo cambiar)

Un `REVERSE` simplemente invierte la marcha y recalcula desde la nueva orientación al siguiente waypoint. No requiere estructura de datos especial.

### Replanificación

Si la ruta se bloquea (segmento ocupado por otro tren), el AutoPilot:
1. Espera N ticks (configurable, ej. 300 = 15 segundos)
2. Recalcula la ruta desde la posición actual
3. Si sigue bloqueado → vuelve a esperar y reintenta
4. Solo pasa a MANUAL si la ruta es **estructuralmente imposible** (topología cambió, waypoint desapareció, el tren descarriló)

---

## 3. AutoPilot (Control Automático)

### Ciclo de decisión (cada tick)

```
1. ¿Estoy en modo AUTOMÁTICO? → seguir
2. ¿He llegado al waypoint actual?
   - SÍ → ejecutar comandos del waypoint → avanzar al siguiente
   - NO → seguir
3. ¿Tengo ruta calculada?
   - NO → calcular A* hasta el siguiente waypoint
4. ¿El siguiente segmento está libre?
   - SÍ → si hay fork, cambiarlo → avanzar
   - NO → esperar (o recalcular si timeout)
5. ¿Velocidad correcta?
   - Ajustar velocidad según distancia al próximo waypoint
```

### Gestión de forks

En modo AUTOMÁTICO, el sistema puede cambiar cualquier fork que esté en la ruta del tren, **incluso si el BlockManager lo tiene bloqueado**. Esto resuelve [el problema identificado en #107] donde los forks bloqueados impedían maniobras.

Reglas:
- Si el fork está en la ruta Y el tren está en modo AUTO → cambiar sin restricción
- Si el fork está en la ruta Y el tren está en modo MANUAL → NO cambiar (el jugador decide)
- Si el fork NO está en la ruta de ningún tren → comportamiento normal (ADR-005)

### Inversión atómica

Al ejecutar `REVERSE`:
1. Parada completa (speed=0)
2. `releaseAll()` de segmentos que ya no están en la nueva ruta
3. `toggleReversed()`
4. Recalcular ruta desde posición actual

### Fallback a manual

El AutoPilot solo devuelve el control a MANUAL si:
- La ruta es estructuralmente imposible (topología cambió, el waypoint ya no existe)
- El tren sufre un choque o descarrilamiento
- El jugador pulsa la tecla de modo manual

Un bloqueo temporal NUNCA provoca fallback — solo espera y reintenta.

---

## 4. Modo Dual (MANUAL vs AUTOMÁTICO)

Cada tren tiene un flag `mode: MANUAL | AUTOMÁTICO`.

| Aspecto | MANUAL | AUTOMÁTICO |
|---------|--------|-------------|
| Velocidad | Jugador | AutoPilot |
| Dirección | Jugador | AutoPilot |
| Forks | Jugador (si no bloqueados) | Sistema (sin restricción) |
| Link/Unlink | Jugador | Bloqueado |
| Colisión | Contacto/Crash normal | Frenada emergencia + reintento |

Transición MANUAL → AUTO:
- El jugador pulsa una tecla en modo DRIVE
- Solo si el tren tiene un itinerario asignado
- Solo si existe ruta válida al primer waypoint
- Solo si el tren está parado (speed=0)

Transición AUTO → MANUAL:
- El jugador pulsa la tecla de modo
- El jugador cambia la velocidad manualmente (incSpeed/decSpeed)
- Choque o descarrilamiento

### Asignación de itinerarios

- En el editor de itinerarios, se asigna un itinerario a uno o varios trenes (relación 1:N)
- Un tren con itinerario asignado NO está en automático hasta que el jugador lo active
- El jugador decide cuándo arrancar cada tren en automático desde el modo DRIVE

---

## 5. Integración con el sistema actual

### Nuevas clases

| Clase | Responsabilidad |
|-------|----------------|
| `Waypoint` | Record: tipo, id, comandos |
| `WaypointCommand` | Enum: LOAD, UNLOAD, REVERSE, WAIT, SPEED, NONE |
| `AutoPilot` | Lógica de navegación por tick |
| `SegmentPathfinder` | A* sobre RailwayGraph |
| `Itinerary` (refactor) | `List<Waypoint>` + `currentIndex` + `state` (ACTIVE/PAUSED/DONE). Sustituye al actual `List<Stop>` |

### Modificaciones

| Clase | Cambio |
|-------|--------|
| `Train` | Añadir `mode` (MANUAL/AUTO) y `AutoPilot` |
| `Locomotive.update()` | Si AUTO → delegar en AutoPilot |
| `ForkRailTrack.flipRoute()` | Si el tren está en AUTO → permitir aunque esté locked |
| `Gdx3DInputHandler` | Tecla en modo DRIVE para toggle MANUAL/AUTO; cambio de velocidad → AUTO→MANUAL |
| `Itinerary` | Refactorizar: `List<Waypoint>` + estado en vez de `List<Stop>` |

### UI

- Modo AUTO → línea de dirección en AZUL (en vez de verde/rojo)
- Itinerario actual visible en HUD
- Siguiente waypoint marcado en el mapa

---

## 6. Implementación por fases

| Fase | Qué | Prioridad |
|------|-----|-----------|
| 1 | `Waypoint`, `WaypointCommand`, `Itinerary` (datos puros) | Alta |
| 2 | `SegmentPathfinder.find()` — función pura, solo A* | Alta |
| 3 | `AutoPilot` — ejecuta la ruta: sigue segmentos, cambia forks, controla velocidad | Alta |
| 4 | Modo dual (MANUAL/AUTO) en Train + UI | Alta |
| 5 | Comandos REVERSE, WAIT, SPEED | Media |
| 6 | Fallback, replanificación, detección de llegada a waypoint | Media |
| 7 | Editor de itinerarios vía DSL (ver `ADR-009-Itinerary-Editor`) | Implementado |

---

*Última actualización: 2026-05-18*
