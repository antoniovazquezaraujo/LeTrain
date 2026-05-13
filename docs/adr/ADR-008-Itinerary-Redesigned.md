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

- **Heurística**: distancia Manhattan entre centros de segmento
- **Coste**: 1 por segmento (todos los segmentos pesan igual)
- **Restricción de entrada**: si el waypoint destino tiene `entryDir`, solo se aceptan rutas cuyo último segmento tenga conexión física en esa dirección
- **Resultado**: lista ordenada de `Segment` desde el actual hasta el destino

### Tramos independientes (REVERSE)

Un `REVERSE` parte el itinerario en tramos. Cada tramo se resuelve por separado:

```
[Est 3] → [Sensor 7] → [Sensor 2 REVERSE] → [Est 5]

Tramo 1: posición actual → Sensor 2       [A* normal]
Tramo 2: Sensor 2 (invertido) → Est 5     [A* nuevo]
```

- El pathfinder solo calcula de waypoint a waypoint dentro del mismo tramo
- Si un tramo pasa dos veces por el mismo segmento (ida y vuelta), son tramos distintos → sin conflicto
- Al ejecutar REVERSE, el AutoPilot invierte la marcha y recalcula desde cero

### Replanificación

Si la ruta se bloquea (segmento ocupado por otro tren), el AutoPilot:
1. Espera N ticks (configurable)
2. Recalcula la ruta desde la posición actual
3. Si no hay ruta alternativa → frena y devuelve control a MANUAL

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

El AutoPilot devuelve el control a MANUAL si:
- No encuentra ruta al siguiente waypoint
- La ruta está bloqueada más de N ticks sin alternativa
- El tren sufre un choque o descarrilamiento
- El jugador pulsa la tecla de modo manual

---

## 4. Modo Dual (MANUAL vs AUTOMÁTICO)

Cada tren tiene un flag `mode: MANUAL | AUTOMÁTICO`.

| Aspecto | MANUAL | AUTOMÁTICO |
|---------|--------|-------------|
| Velocidad | Jugador | AutoPilot |
| Dirección | Jugador | AutoPilot |
| Forks | Jugador (si no bloqueados) | Sistema (sin restricción) |
| Link/Unlink | Jugador | Bloqueado |
| Colisión | Contacto/Crash normal | Frenada emergencia + fallback manual |

Transición MANUAL → AUTO:
- Solo si existe ruta válida al primer waypoint
- Solo si el tren está parado (speed=0)

Transición AUTO → MANUAL:
- Siempre permitida (jugador o fallback)

---

## 5. Integración con el sistema actual

### Nuevas clases

| Clase | Responsabilidad |
|-------|----------------|
| `Waypoint` | Record: tipo, id, comandos |
| `WaypointCommand` | Enum: LOAD, UNLOAD, REVERSE, WAIT, SPEED, NONE |
| `AutoPilot` | Lógica de navegación por tick |
| `SegmentPathfinder` | A* sobre RailwayGraph |
| `Itinerary` (refactor) | Lista de Waypoints + estado actual |

### Modificaciones

| Clase | Cambio |
|-------|--------|
| `Train` | Añadir `mode` (MANUAL/AUTO) y `AutoPilot` |
| `Locomotive.update()` | Si AUTO → delegar en AutoPilot |
| `ForkRailTrack.flipRoute()` | Si el tren está en AUTO → permitir aunque esté locked |
| `Gdx3DInputHandler` | Tecla para toggle MANUAL/AUTO |

### UI

- Modo AUTO → línea de dirección en AZUL (en vez de verde/rojo)
- Itinerario actual visible en HUD
- Siguiente waypoint marcado en el mapa

---

## 6. Implementación por fases

| Fase | Qué | Prioridad |
|------|-----|-----------|
| 1 | `Waypoint`, `WaypointCommand`, `Itinerary` (datos) | Alta |
| 2 | `SegmentPathfinder` (A*) | Alta |
| 3 | `AutoPilot` (navegación básica: seguir ruta, cambiar forks, velocidad) | Alta |
| 4 | Modo dual (MANUAL/AUTO) en Train + UI | Alta |
| 5 | Comandos REVERSE, WAIT, SPEED | Media |
| 6 | Fallback y replanificación | Media |
| 7 | Editor visual de itinerarios | Baja |

---

*Última actualización: 2026-05-13*
