# ADR-010: Plan de Tests del AutoPilot

## Estado: EN DISEÑO

## Objetivo

Cubrir todos los casos de uso del AutoPilot con tests de integración sobre layouts reales
(no mocks). Cada test construye una vía, coloca tren y estaciones, ejecuta el itinerario,
y verifica el resultado.

---

## Layouts y Casos

### 1. Vía recta, 2 estaciones

```
[Madrid]═══════[Barcelona]
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 1.1 | Madrid → Barcelona | Llega a Barcelona |
| 1.2 | Madrid(SPEED 5) → Barcelona(STOP) | Crucero a 5, frena en Barcelona |
| 1.3 | Madrid(LOAD) → Barcelona(UNLOAD) | Carga en Madrid, descarga en Barcelona |
| 1.4 | Tren fuera de Madrid → Madrid → Barcelona | Va primero a Madrid, luego a Barcelona |

### 2. Vía recta, 3 estaciones

```
[Madrid]═══[Zaragoza]═══[Barcelona]
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 2.1 | Madrid → Zaragoza → Barcelona | Pasa por las 3 en orden |
| 2.2 | Madrid → Barcelona | Salta Zaragoza, va directo |
| 2.3 | Madrid(SPEED 3) → Barcelona(SPEED 5) | Velocidad 3 en primer tramo, 5 en el segundo |

### 3. Circuito cerrado simple

```
    ╔══════╗
    ║      ║
[Madrid] [Barcelona]
    ║      ║
    ╚══════╝
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 3.1 | Madrid → Barcelona | Encuentra ruta por el circuito |
| 3.2 | Madrid → Barcelona → Madrid (vuelta completa) | Da la vuelta |

### 4. Circuito con bifurcación (fork)

```
        ╔══[Zaragoza]══╗
        ║              ║
[Madrid]╣              ╠══[Barcelona]
        ╚══════════════╝
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 4.1 | Madrid → Zaragoza → Barcelona | Fork arranca en ruta directa. AutoPilot lo cambia para tomar el desvío a Zaragoza |
| 4.2 | Madrid → Barcelona (ruta directa) | No toma el desvío |
| 4.3 | Madrid → Zaragoza, tren en vía contraria | No implementado aún |

### 5. Dos trenes

```
[Madrid]═══════[Barcelona]
  T1 →           ← T2
```

| # | Escenario | Esperado |
|---|----------|----------|
| 5.1 | T1 va a Barcelona, T2 va a Madrid | No chocan si hay bloqueo |
| 5.2 | T2 parado en Barcelona, T1 va a Barcelona | T1 espera detrás |
| 5.3 | T1 y T2 en misma dirección | Se siguen sin chocar |

### 6. Callejón sin salida

```
[Madrid]═══════╗
               ║
               ║
            [Barcelona]
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 6.1 | Madrid → Barcelona | Llega sin pasarse (no hay vía más allá) |
| 6.2 | Madrid → Barcelona → Madrid | REVERSE en Barcelona para volver |

### 7. Sensores como waypoints

```
[Madrid]═══{Sensor}═══[Barcelona]
```

| # | Itinerario | Esperado |
|---|-----------|----------|
| 7.1 | Madrid → Sensor(REVERSE) → Barcelona | Invierte en el sensor |
| 7.2 | Madrid → Sensor(WAIT 10) → Barcelona | Espera 10 segundos |

### 8. Múltiples acciones por waypoint

```
[Madrid]═══════[Barcelona]
```

| # | Comandos en Madrid | Esperado |
|---|-------------------|----------|
| 8.1 | LOAD REVERSE | Carga e invierte |
| 8.2 | LOAD WAIT 5 SPEED 5 | Carga, espera 5s, sale a 5 |
| 8.3 | WAIT 10 STOP | Espera 10 segundos y para |

### 9. Estados del AutoPilot

| # | Escenario | Esperado |
|---|----------|----------|
| 9.1 | Activar con tren en movimiento | `activate()` retorna false |
| 9.2 | Activar sin pathfinder | `activate()` retorna false |
| 9.3 | Activar sin itinerario | `activate()` retorna false |
| 9.4 | Itinerario con <2 waypoints | `isValid()` = false, no activa |
| 9.5 | ↑↓ en AUTO → desactiva | Pasa a MANUAL |
| 9.6 | `a` en MANUAL con itinerario → activa | Pasa a AUTO |
| 9.7 | `a` en MANUAL sin itinerario → no activa | Sigue MANUAL |
| 9.8 | `a` con itinerario DONE → resetea y reactiva | Vuelve al primer waypoint |

### 10. Casos DSL

| # | Programa DSL | Esperado |
|---|-------------|----------|
| 10.1 | `station 1 set name "X"` | `getName()` devuelve "X" |
| 10.2 | `add station "X" LOAD` (por nombre) | Resuelve estación por nombre |
| 10.3 | `add station 1 LOAD` (por ID) | Resuelve estación por ID |
| 10.4 | Múltiples comandos `LOAD REVERSE STOP` | Se ejecutan en orden |
| 10.5 | Error de sintaxis | El parser reporta error, no crashea |

---

## Cómo implementar los tests

Cada test de layout:
1. Crea un `Model` con el mapa de vías
2. Coloca estaciones y tren
3. Ejecuta `model.setProgram(dsl)` para parsear el itinerario
4. Activa autopilot
5. Ejecuta N ticks de simulación
6. Verifica: posición del tren, velocidad, modo, estación actual

---

## Prioridad

1. **Casos 1 (vía recta)** — lo básico, debería funcionar ya
2. **Casos 4 (fork)** — lo que está fallando ahora
3. **Casos 3 (circuito)** — relacionado con forks
4. **Casos 9 (estados)** — transiciones MANUAL/AUTO
5. **Casos 6 (dead end)** — frenada al final
6. Resto — según necesidad
