# Informe de Análisis: Fallo del Sistema de Cantones en Apartaderos (Siding Bypass & Wakeup)

Este informe analiza en detalle el funcionamiento del sistema de cantones y desvíos lógicos en **LeTrain**, explicando por qué los trenes autónomos se detienen ante un apartadero bloqueado y no reanudan la marcha cuando una de las vías del apartadero queda libre.

---

## 1. Arquitectura del Sistema de Cantones y Bloqueos

El sistema de seguridad de **LeTrain** gestiona el movimiento de trenes mediante reservas de **Segmentos** (cantones) lógicos utilizando la clase [BlockManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/BlockManager.java):
* **Segmento (Segment):** Tramo de vía indivisible delimitado por puntos lógicos de decisión (desvíos o topes de final de vía).
* **Gestor de Seguridad (TrainSafetyManager):** Responsable de garantizar que el tren actual posea los segmentos físicos donde se encuentra y reserve de manera predictiva el siguiente segmento (`nextSegment`) en su ruta.
* **Piloto Automático (AutoPilot):** Gestiona los itinerarios y genera la ruta planificada de segmentos.

Cuando un tren autónomo entra en un segmento, intenta bloquear de forma predictiva el siguiente segmento (`nextSegment`) a través del método `tryLock`. Si esta llamada retorna `false` (porque el segmento de destino está ocupado por otro tren), el tren inicia inmediatamente el frenado de emergencia y pasa al estado de espera preventiva (`isWaitingForBlock = true`, velocidad objetivo establecida a 0).

---

## 2. Evasión de Apartaderos Ocupados (Siding Bypass)

Para evitar que los trenes se queden bloqueados en desvíos/apartaderos cuando la vía principal del apartadero está ocupada, la clase [TrainSafetyManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java) implementa el método `tryAlternativeSegment(Model model)`:
1. Si el bloqueo de `nextSegment` falla, se verifica si el segmento es elegible para desvío (no tiene paradas programadas de waypoints).
2. Se obtienen los dos nodos extremos (desvíos/Forks) de dicho segmento.
3. Se itera por los puertos del nodo de entrada buscando si existe una **vía paralela alternativa** (otro segmento que conecte los mismos dos desvíos extremos).
4. Si se encuentra un segmento alternativo (`sAlt`) y está libre, el gestor:
   * Reserva el segmento alternativo.
   * Modifica dinámicamente la ruta del piloto automático reemplazando el segmento original por el alternativo (`replaceRouteSegment`).
   * Realinea el desvío físico en la dirección de la vía alternativa (`ensureForkRoute`).
   * El tren continúa la marcha por el apartadero libre.

*Este proceso funciona correctamente siempre y cuando la vía alternativa esté libre en el instante en que el tren llega y evalúa su ruta por primera vez.*

---

## 3. Causa Raíz del Bloqueo Permanente (Deadlock Lógico al Despertar)

El fallo reportado por el usuario ocurre cuando **ambas vías del apartadero (principal y alternativa) están ocupadas** al aproximarse el tren. El flujo lógico que desencadena el cuelgue permanente es el siguiente:

### A. Detención Inicial del Tren
1. El Tren 2 se aproxima al apartadero.
2. Intenta bloquear su vía planificada original (Vía A), que está ocupada. El bloqueo falla.
3. Intenta buscar la vía alternativa (Vía B), pero ésta también está ocupada. El bloqueo de la alternativa también falla.
4. Al no haber vías libres, el Tren 2 frena hasta detenerse por completo delante del desvío de entrada. Su estado cambia a `isWaitingForBlock = true`, y su variable `nextSegment` permanece apuntando a la **Vía A** (el segmento planificado de su ruta original).

### B. El Evento de Liberación de Vía
1. Posteriormente, el tren que ocupaba la Vía B (la vía alternativa) reanuda la marcha y la abandona completamente.
2. El `BlockManager` libera la Vía B y lanza el evento `onReleaseListener`.
3. El escuchador global de liberación configurado en [Model.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/Model.java#L152-L163) reacciona para despertar a los trenes parados:
   ```java
   bmi.setOnReleaseListener((releasedSegment) -> {
       for (Locomotive loco : locomotives) {
           Train train = loco.getTrain();
           if (train != null && train.isAutoMode()) {
               letrain.segments.Segment nextSeg = train.getSafetyManager().getNextSegment();
               if (train.getSafetyManager().isWaitingForBlock() && releasedSegment.equals(nextSeg)) {
                   train.getSafetyManager().onBlockReleased();
               }
           }
       }
   });
   ```
4. **El fallo lógico clave:** La comparación `releasedSegment.equals(nextSeg)` requiere obligatoriamente que la vía física liberada coincida exactamente con el `nextSegment` del tren que espera.
5. Dado que la vía liberada es la Vía B (la alternativa) y el `nextSegment` del Tren 2 es la Vía A (la planificada), la comparación da `false`.
6. El Tren 2 **nunca recibe la llamada** `onBlockReleased()`.
7. El Tren 2 permanece en estado de frenado permanente, a pesar de que la vía del apartadero alternativa está completamente libre.

---

## 4. Riesgo de Bucle Infinito en la Selección de Locomotoras

Durante la inicialización y testeo de la solución, se identificó un problema de bucle infinito potencial en los métodos `selectNextLocomotive()` y `selectPrevLocomotive()` de [Model.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/Model.java#L520-L538):
```java
do {
    selectedLocomotiveIndex++;
    if (selectedLocomotiveIndex >= getLocomotives().size()) selectedLocomotiveIndex = 0;
    selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
} while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex < getLocomotives().size());
```
Si un tren se acopla o crea en un test/partida y ninguna de sus locomotoras está asignada explícitamente como la principal/directora (por ejemplo, al no llamarse a `assignDefaultDirectorLinker()`), `isDirectorLinker()` de todas las locomotoras de la lista retornará `false`.
El bucle anterior incrementará y reiniciará el índice indefinidamente (ya que `selectedLocomotiveIndex < size` siempre se cumple debido al módulo/wrap a 0), congelando por completo la aplicación y consumiendo el 100% de CPU.

---

## 5. Propuesta de Solución Técnica

Para resolver estos dos fallos de diseño lógico, se proponen las siguientes modificaciones en el código de producción cuando el usuario lo autorice:

### A. Implementar `isAlternativeSegment` en `TrainSafetyManager`
Permitir que el gestor de seguridad compruebe si un segmento candidato es una vía paralela que conecta los mismos dos nodos extremos que `nextSegment`:
```java
// En letrain.vehicle.rail.impl.TrainSafetyManager
@Override
public boolean isAlternativeSegment(Segment candidate) {
    if (nextSegment == null || candidate == null || candidate.equals(nextSegment)) {
        return false;
    }
    RailwayGraph graph = this.train.getModel().getRailwayGraph();
    Pair<Port, Port> ports = nextSegment.getPorts();
    if (ports == null || ports.getFirst() == null || ports.getSecond() == null) {
        return false;
    }
    RailNode node1 = ports.getFirst().getNode();
    RailNode node2 = ports.getSecond().getNode();
    if (node1 == null || node2 == null) {
        return false;
    }
    Pair<Port, Port> altPorts = candidate.getPorts();
    if (altPorts == null || altPorts.getFirst() == null || altPorts.getSecond() == null) {
        return false;
    }
    RailNode altNode1 = altPorts.getFirst().getNode();
    RailNode altNode2 = altPorts.getSecond().getNode();
    return (altNode1.equals(node1) && altNode2.equals(node2)) ||
           (altNode1.equals(node2) && altNode2.equals(node1));
}
```

### B. Actualizar el Listener de Liberación en `Model.java`
Modificar el escuchador para que despierte al tren si la vía liberada es su destino planificado directo **o** una vía paralela de apartadero:
```java
// En letrain.mvp.impl.Model
bmi.setOnReleaseListener((releasedSegment) -> {
    for (Locomotive loco : locomotives) {
        Train train = loco.getTrain();
        if (train != null && train.isAutoMode()) {
            letrain.vehicle.rail.TrainSafetyManager safety = train.getSafetyManager();
            letrain.segments.Segment nextSeg = safety.getNextSegment();
            if (safety.isWaitingForBlock() && 
               (releasedSegment.equals(nextSeg) || safety.isAlternativeSegment(releasedSegment))) {
                safety.onBlockReleased();
            }
        }
    }
});
```

### C. Asegurar Selección de Locomotoras en `Model.java`
Evitar el bucle infinito controlando si el buscador de locomotora seleccionada vuelve a su posición de inicio sin haber encontrado directores:
```java
// En letrain.mvp.impl.Model
@Override public boolean selectNextLocomotive() {
    if (getLocomotives().isEmpty()) return false;
    int start = selectedLocomotiveIndex;
    do {
        selectedLocomotiveIndex++;
        if (selectedLocomotiveIndex >= getLocomotives().size()) selectedLocomotiveIndex = 0;
        selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        if (selectedLocomotiveIndex == start) {
            break;
        }
    } while (!selectedLocomotive.isDirectorLinker());
    return true;
}
```
