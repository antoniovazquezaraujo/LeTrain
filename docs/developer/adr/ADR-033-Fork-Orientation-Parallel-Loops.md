# ADR-033: Orientación de agujas direction-aware en bucles de segmentos paralelos

## Estado: IMPLEMENTADO ([#830])

## Contexto

Un savegame del dueño (`fallo.json`, no versionado) tiene un bucle cerrado con **tres segmentos
paralelos entre las mismas dos agujas**:

- `S0`: estaciones 1, 3, 2 (vía principal).
- `S1`: estación 4 (bucle intermedio).
- `S2`: estación 5.

El itinerario `1 → 2 → 4 → 1 → 2 → 5` es válido sin `reverse` (el bucle es cerrado), pero el tren
hacía siempre `1 → 2 → 5`: **nunca entraba en S1** y no pasaba por la estación 4.

Evidencia (con ambas agujas en `usingAlternativeRoute = false`):

- `ensureForkRoute(S0, S1)` **no cambiaba ninguna aguja**.
- `ensureForkRoute(S0, S2)` **sí cambiaba una** (la aguja equivocada).

A* encontraba ruta para todos los pares, así que el fallo era de **actuación de la aguja**, no de
planificación.

## Causa raíz (dos defectos)

1. **Se elegía la aguja equivocada.** `AutoPilotImpl.ensureForkRoute` recorría los puertos de
   `from` y se quedaba con el **primer nodo compartido** con `to`. En un bucle de segmentos
   paralelos ambos extremos se comparten: el primero es la aguja **de entrada** (la que el tren ya
   dejó atrás), no la de **salida** (la que va a tomar). Pedir S1 orientaba la aguja de atrás y
   dejaba la de delante en la rama principal.
2. **`RailNodeImpl.setRoute` asumía A = normal, B = alternativa.** Los puertos A/B son un mapeo
   físico fijo, pero `ForkRouter` puede reetiquetar `originalRoute`/`alternativeRoute`; en este
   bucle los A/B estaban cruzados respecto a las etiquetas vivas. Pedir la rama de S1 era un
   **no-op** y pedir la de S2 **conmutaba S1**. (En el save concreto el mapeo resultó coincidir,
   pero el defecto era real y quedó cubierto por un test con A/B cruzados.)

## Decisión

### 1. `ensureForkRoute` direction-aware

- Se resuelve el **puerto de salida real** de `from` con `getTrainExitPort(from)` (el que el tren
  va a tomar según su sentido físico).
- Se usa la aguja de **ese nodo** y se busca el puerto de `to` en el mismo nodo.
- El **fallback** al primer nodo compartido queda solo para llamadas sin tren/dirección (p. ej.
  `replaceRouteSegment` sobre segmentos que el tren no ocupa) o cuando `to` no es adyacente a la
  salida.
- Al orientar la aguja de salida al entrar en cada segmento, se cubren también los bucles en los
  que el tren entra por una punta de S1 y sale por la otra.

### 2. Selección normal/alternativa por dirección física

- `RailNodeImpl.setRoute`, `isRouteActive` y `getActiveExit` comparan la **dirección física** del
  puerto (`getDirForPort(exit.getType())`) con los valores **vivos** de
  `originalRoute`/`alternativeRoute` del `ForkRouter`.
- **No se cambia el mapeo A/B**: los puertos siguen siendo un mapeo físico fijo construido en
  `initializePorts`. Lo que cambia es cómo se interpreta al rutar: la etiqueta
  normal/alternativa se decide por la dirección de la rama, no por el nombre del puerto.

## Consecuencias

- **Bucles y passing loops** con dos nodos compartidos: la aguja que el tren tiene delante es la
  que se orienta, en el sentido de la marcha.
- **Bypass** (`TrainSafetyManager.tryAlternativeSegment` → `AutoPilotImpl.replaceRouteSegment`):
  al reemplazar un segmento por la variante paralela, `ensureForkRoute` elige la aguja de salida
  correcta; el análisis histórico de `siding_bypass_analysis.md` se conserva con una nota de
  actualización. **Limitación actual:** `tryAlternativeSegment` prueba **el primer** segmento
  paralelo que encuentra (rompe el bucle al primero) y, si ese también está ocupado, **no prueba el
  siguiente**; no promete “cualquier paralelo”.
- **Acciones de fork** (`fork N set curved|flip`) siguen usando el mismo `ForkRouter`; el criterio
  físico no las altera.
- **Tests de regresión**:
  - `RailNodeImplRouteTest`: `setRoute`/`isRouteActive` con A/B cruzados respecto a
    original/alternativa deben activar la rama física correcta.
  - `ForkLoopOrientationTest`: bucle triple S0/S1/S2 con dos agujas compartidas;
    `ensureForkRoute(S0,S1)` activa la rama de S1 en la aguja de salida (la de entrada no se
    toca) y `ensureForkRoute(S0,S2)` la de S2. Además, el itinerario `1→2→4→1→2→5` pasa por la
    estación 4 y sigue a 1, 2 y 5 sin invertir.
- **Verificación end-to-end** con `fallo.json` (no commiteado): la secuencia de estaciones es
  `[1, 3, 2, 4, 1, 3, 2, 5]` (la 3 se pasa de camino) **sin invertir**.

## Alternativas descartadas

- **Probar el segundo nodo compartido a ciegas**: solo funciona para una orientación concreta del
  bucle; en otras topologías o sentidos elige la aguja de atrás. No usa la dirección del tren.
- **Reasignar A/B al construir el nodo**: el mapeo se construye una vez y `ForkRouter` puede
  reetiquetar después; no arregla los cambios posteriores y rompe el contrato de que A/B es un
  mapeo físico fijo. La comparación con las etiquetas vivas es más robusta.
- **Re-planificar la ruta tras conmutar**: caro e innecesario; A* ya devolvía la ruta correcta. El
  fallo era la selección/actuación de la aguja, no el plan.
- **Orientar ambas agujas compartidas**: perturba tráfico ajeno y no es direction-aware; el
  requisito es orientar la aguja que el tren va a tomar.

[#830]: https://github.com/antoniovazquezaraujo/letrain/issues/830
