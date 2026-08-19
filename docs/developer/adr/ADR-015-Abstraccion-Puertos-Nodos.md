# ADR-015: Abstracción de Puertos (PortType) y Tránsitos en Nodos

## Estado
ACEPTADA

## Contexto
Actualmente, la interfaz [PathStep](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/PathStep.java) acopla la representación lógica del grafo con la orientación física en el mapa mediante el uso del enumerado `Dir` (direcciones geográficas). Esto acopla el grafo ferroviario y el pathfinding con la brújula y las coordenadas físicas de la rejilla, dificultando la abstracción y aumentando la complejidad de gestión de bifurcaciones (Fork tracks) en el piloto automático.

Se propone reemplazar el uso de direcciones geográficas por una abstracción basada en **puertos lógicos con roles definidos (`PortType`)** y el tipo de transición al atravesar un nodo (**converger** vs. **diverger**).

---

## Interfaces y Clases Lógicas del Grafo

A continuación se detalla cómo quedarían definidos todos los contratos (`interfaces`) y sus implementaciones correspondientes en el paquete `letrain.segments`.

### 1. El Tipo de Puerto (`PortType`) y el Puerto (`Port` / `PortImpl`)
Sustituye a `PathStep`. Representa un "extremo de conexión" u "puerta de enlace" en un nodo.

```java
package letrain.segments;

public enum PortType {
    TRUNK,  // El tronco común / entrada principal (o el único extremo de un DeadEnd)
    A,      // La rama A de un desvío (ruta por defecto)
    B       // La rama B de un desvío (ruta alternativa)
}
```

```java
package letrain.segments;

/**
 * Representa un extremo o puerto de conexión lógica en un nodo.
 */
public interface Port {
    RailNode getNode();
    PortType getType();
}
```

```java
package letrain.segments.impl;

import java.util.Objects;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.segments.RailNode;

public class PortImpl implements Port {
    private final RailNode node;
    private final PortType type;

    public PortImpl(RailNode node, PortType type) {
        this.node = node;
        this.type = type;
    }

    @Override
    public RailNode getNode() {
        return node;
    }

    @Override
    public PortType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortImpl port = (PortImpl) o;
        return type == port.type && Objects.equals(node, port.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, type);
    }

    @Override
    public String toString() {
        return "Port{" + node + ", type=" + type + "}";
    }
}
```

---

### 2. El Nodo (`RailNode` / `RailNodeImpl`)
Representa una bifurcación (`ForkRailTrack`) o un fin de vía (`DeadEnd`). Encapsula la lógica física de las vías y proporciona una interfaz limpia de puertos al exterior.

```java
package letrain.segments;

import java.util.List;
import letrain.track.Track;

public interface RailNode {
    /**
     * Devuelve el objeto físico de vía asociado.
     */
    Track getTrack();

    /**
     * Devuelve la lista de todos los puertos disponibles en este nodo.
     */
    List<Port> getPorts();

    /**
     * Devuelve el tipo de transición al cruzar del puerto de entrada al de salida.
     */
    TransitionType getTransitionType(Port entry, Port exit);

    /**
     * Configura el desvío físico para habilitar el paso desde un puerto a otro.
     * Devuelve true si provocó un cambio físico de agujas.
     */
    boolean setRoute(Port entry, Port exit);

    /**
     * Indica si el camino entre dos puertos está activo en este momento.
     */
    boolean isRouteActive(Port entry, Port exit);

    /**
     * Dado un puerto de entrada, devuelve el puerto por el que saldrá el tren.
     */
    Port getActiveExit(Port entry);
}
```

```java
package letrain.segments;

public enum TransitionType {
    DIVERGING,  // Entrada por tronco (TRUNK) hacia una de las ramas (A o B)
    CONVERGING, // Entrada por rama (A o B) saliendo hacia el tronco (TRUNK)
    BLOCKED     // Paso inválido (ej: rama A a rama B, o cualquier paso en DeadEnd)
}
```

```java
package letrain.segments.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.segments.RailNode;
import letrain.segments.TransitionType;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;

public class RailNodeImpl implements RailNode {
    private final Track track;
    private final List<Port> ports = new ArrayList<>();
    private final Map<Dir, Port> dirToPort = new HashMap<>();
    private final Map<PortType, Dir> portToDir = new HashMap<>();

    public RailNodeImpl(Track track) {
        this.track = track;
        initializePorts();
    }

    private void initializePorts() {
        if (track instanceof ForkRailTrack fork) {
            // Mapeo físico de un desvío a puertos lógicos
            Dir trunkDir = fork.getOriginalRoute().getKey();         // PortType.TRUNK
            Dir normalDir = fork.getOriginalRoute().getValue();      // PortType.A
            Dir alternativeDir = fork.getAlternativeRoute().getValue(); // PortType.B

            addPortMapping(PortType.TRUNK, trunkDir);
            addPortMapping(PortType.A, normalDir);
            addPortMapping(PortType.B, alternativeDir);
        } else {
            // Fin de vía (DeadEnd) tiene un solo puerto conectado
            List<Dir> connections = new ArrayList<>(track.getConnections());
            if (!connections.isEmpty()) {
                addPortMapping(PortType.TRUNK, connections.get(0));
            }
        }
    }

    private void addPortMapping(PortType type, Dir dir) {
        Port port = new PortImpl(this, type);
        ports.add(port);
        dirToPort.put(dir, port);
        portToDir.put(type, dir);
    }

    public Port getPortForDir(Dir dir) {
        return dirToPort.get(dir);
    }

    public Dir getDirForPort(PortType type) {
        return portToDir.get(type);
    }

    @Override
    public Track getTrack() {
        return track;
    }

    @Override
    public List<Port> getPorts() {
        return ports;
    }

    @Override
    public TransitionType getTransitionType(Port entry, Port exit) {
        if (entry.getNode() != this || exit.getNode() != this) {
            return TransitionType.BLOCKED;
        }
        if (!(track instanceof ForkRailTrack)) {
            return TransitionType.BLOCKED; // Un DeadEnd no tiene transición de salida
        }

        PortType in = entry.getType();
        PortType out = exit.getType();

        if (in == PortType.TRUNK && (out == PortType.A || out == PortType.B)) {
            return TransitionType.DIVERGING;
        }
        if ((in == PortType.A || in == PortType.B) && out == PortType.TRUNK) {
            return TransitionType.CONVERGING;
        }
        return TransitionType.BLOCKED;
    }

    @Override
    public boolean setRoute(Port entry, Port exit) {
        if (getTransitionType(entry, exit) != TransitionType.DIVERGING) {
            return false; // Solo las transiciones divergentes requieren cambiar agujas
        }

        ForkRailTrack fork = (ForkRailTrack) track;
        if (exit.getType() == PortType.A) {
            if (fork.isUsingAlternativeRoute()) {
                fork.setNormalRoute();
                return true;
            }
        } else if (exit.getType() == PortType.B) {
            if (!fork.isUsingAlternativeRoute()) {
                fork.setAlternativeRoute();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRouteActive(Port entry, Port exit) {
        TransitionType type = getTransitionType(entry, exit);
        if (type == TransitionType.BLOCKED) return false;
        if (type == TransitionType.CONVERGING) return true; // Converger siempre es físicamente pasable

        ForkRailTrack fork = (ForkRailTrack) track;
        boolean usingAlt = fork.isUsingAlternativeRoute();
        return (exit.getType() == PortType.B && usingAlt) 
            || (exit.getType() == PortType.A && !usingAlt);
    }

    @Override
    public Port getActiveExit(Port entry) {
        if (!(track instanceof ForkRailTrack)) return null;
        if (entry.getType() == PortType.A || entry.getType() == PortType.B) {
            return getPortByType(PortType.TRUNK); // Converger siempre va al tronco
        }
        
        ForkRailTrack fork = (ForkRailTrack) track;
        PortType activeType = fork.isUsingAlternativeRoute() ? PortType.B : PortType.A;
        return getPortByType(activeType);
    }

    private Port getPortByType(PortType type) {
        return ports.stream()
                .filter(p -> p.getType() == type)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RailNodeImpl railNode = (RailNodeImpl) o;
        return Objects.equals(track.getPosition(), railNode.track.getPosition());
    }

    @Override
    public int hashCode() {
        return Objects.hash(track.getPosition());
    }

    @Override
    public String toString() {
        if (track instanceof ForkRailTrack fork) {
            return "Fork(" + fork.getId() + ")@" + track.getPosition();
        }
        return "DeadEnd@" + track.getPosition();
    }
}
```

---

### 3. El Segmento (`Segment` / `SegmentImpl`)
Un segmento conecta dos puertos (`Port`) de extremos.

```java
package letrain.segments;

import letrain.utils.Pair;

/**
 * Conexión física única entre dos puertos de nodos distintos.
 */
public interface Segment {
    String getId();
    Pair<Port, Port> getPorts();
}
```

```java
package letrain.segments.impl;

import letrain.segments.Port;
import letrain.segments.Segment;
import letrain.utils.Pair;

public class SegmentImpl implements Segment {
    private final String id;
    private final Pair<Port, Port> ports;

    public SegmentImpl(String id, Port p1, Port p2) {
        this.id = id;
        this.ports = new Pair<>(p1, p2);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Pair<Port, Port> getPorts() {
        return ports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SegmentImpl segment = (SegmentImpl) o;
        return id.equals(segment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "[" + ports.getFirst() + " <-> " + ports.getSecond() + "]";
    }
}
```

---

### 4. El Grafo Ferroviario (`RailwayGraph` / `RailwayGraphImpl`)
El mapa del grafo usa ahora `Port` como unidad lógica de conexión.

```java
package letrain.segments;

import java.util.List;
import letrain.track.Station;
import letrain.track.Sensor;
import letrain.track.rail.RailTrack;

public interface RailwayGraph {
    Segment getSegment(Port port);
    List<Port> getNextPorts(Port current);
    List<Segment> findPath(Segment start, Segment end);
    List<Station> getStations(Segment segment);
    List<Sensor> getSensors(Segment segment);
    Segment getSegment(RailTrack track);
    
    default int getTrackCount(Segment segment) {
        return 0;
    }
}
```

```java
package letrain.segments.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;

public class RailwayGraphImpl implements RailwayGraph {
    private final Map<Port, Segment> portToSegment = new HashMap<>();
    private final Map<RailNode, List<Segment>> nodeToSegments = new HashMap<>();
    private final Map<Segment, List<Station>> segmentToStations = new HashMap<>();
    private final Map<Segment, List<Sensor>> segmentToSensors = new HashMap<>();
    private final Map<RailTrack, Segment> trackToSegment = new HashMap<>();
    private final Map<Segment, Set<RailTrack>> segmentToTracks = new HashMap<>();

    @Override
    public Segment getSegment(Port port) {
        return portToSegment.get(port);
    }

    @Override
    public List<Port> getNextPorts(Port current) {
        Segment s = getSegment(current);
        if (s == null) return null;
        
        // Obtener el puerto en el otro extremo del segmento
        Port targetPort = current.equals(s.getPorts().getFirst()) 
                ? s.getPorts().getSecond() 
                : s.getPorts().getFirst();
        
        RailNode destinationNode = targetPort.getNode();
        
        // De los puertos del nodo destino, devolvemos aquellos que no nos retornen al mismo segmento
        return destinationNode.getPorts().stream()
                .filter(port -> getSegment(port) != s)
                .collect(Collectors.toList());
    }

    @Override
    public List<Segment> findPath(Segment start, Segment end) {
        if (start == null || end == null) return new ArrayList<>();
        if (start.equals(end)) return List.of(start);

        java.util.Queue<Segment> queue = new java.util.LinkedList<>();
        Map<Segment, Segment> parentMap = new HashMap<>();
        Set<Segment> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Segment current = queue.poll();
            if (current.equals(end)) {
                return reconstructPath(parentMap, end);
            }

            for (Segment neighbor : getConnectedSegments(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        return new ArrayList<>();
    }

    private List<Segment> getConnectedSegments(Segment s) {
        List<Segment> neighbors = new ArrayList<>();
        RailNode node1 = s.getPorts().getFirst().getNode();
        RailNode node2 = s.getPorts().getSecond().getNode();

        if (nodeToSegments.containsKey(node1)) neighbors.addAll(nodeToSegments.get(node1));
        if (nodeToSegments.containsKey(node2)) neighbors.addAll(nodeToSegments.get(node2));

        return neighbors.stream()
                .filter(neighbor -> !neighbor.equals(s))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Segment> reconstructPath(Map<Segment, Segment> parentMap, Segment end) {
        List<Segment> path = new java.util.LinkedList<>();
        Segment curr = end;
        while (curr != null) {
            path.add(0, curr);
            curr = parentMap.get(curr);
        }
        return path;
    }

    public void registerSegment(Port port, Segment segment) {
        portToSegment.put(port, segment);
        RailNode node = port.getNode();
        nodeToSegments.computeIfAbsent(node, k -> new ArrayList<>()).add(segment);
    }

    public void registerStation(Segment segment, Station station) {
        segmentToStations.computeIfAbsent(segment, k -> new ArrayList<>()).add(station);
    }

    public void registerSensor(Segment segment, Sensor sensor) {
        segmentToSensors.computeIfAbsent(segment, k -> new ArrayList<>()).add(sensor);
    }

    public void registerTrack(Segment segment, RailTrack track) {
        trackToSegment.putIfAbsent(track, segment);
        segmentToTracks.computeIfAbsent(segment, k -> new HashSet<>()).add(track);
    }

    @Override
    public List<Station> getStations(Segment segment) {
        return segmentToStations.getOrDefault(segment, new ArrayList<>());
    }

    @Override
    public List<Sensor> getSensors(Segment segment) {
        return segmentToSensors.getOrDefault(segment, new ArrayList<>());
    }

    @Override
    public Segment getSegment(RailTrack track) {
        return trackToSegment.get(track);
    }

    @Override
    public int getTrackCount(Segment segment) {
        Set<RailTrack> tracks = segmentToTracks.get(segment);
        return tracks != null ? tracks.size() : 0;
    }
}
```

---

## Mapeo Físico en el Descubrimiento (`TopologyService`)

Durante la inicialización del mapa, el [TopologyServiceImpl](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/impl/TopologyServiceImpl.java) utiliza el método `getPortForDir` para mapear los rastreos geométricos en puertos lógicos:

```java
private Port findOrCreatePort(RailNodeImpl node, Dir dir) {
    // El nodo ya ha autodetectado e inicializado sus puertos en base a su vía física.
    // Simplemente recuperamos el puerto correspondiente a esa dirección cardinal.
    return node.getPortForDir(dir);
}
```

---

## Reglas de Seguridad y Bloques (Gestión Reactiva)

Para cumplir con las directrices del proyecto y evitar el uso de bucles periódicos (`ticks`), la gestión de bloqueos (reservas y liberaciones) de segmentos debe seguir las siguientes reglas operativas:

1. **Sin Polling en Ticks**: Está prohibido implementar comprobaciones periódicas que recorran las baldosas del mapa o los vagones del tren para gestionar bloqueos en el bucle principal de físicas (`tick`).
2. **Reserva Reactiva**: La reserva (bloqueo) de un segmento de vía se desencadena única y exclusivamente cuando la cabeza del tren (el primer linker/locomotora) **entra en un desvío o nodo (`RailNode`)**. En ese instante, se calcula el siguiente segmento y se intenta bloquear.
3. **Liberación Reactiva**: La liberación de un segmento de vía que ha quedado atrás se desencadena única y exclusivamente cuando la cola del tren (el último linker) **sale completamente de un desvío o nodo (`RailNode`)**.

### Cambios en el Contrato del Sistema de Seguridad

Se modifica la interfaz [TrainSafetyManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/TrainSafetyManager.java) reemplazando la lógica de polling por dos métodos de evento puros:

*   **Eliminados**: `onTrackEntered(Track track)` y `releaseOldSegments(BlockManager bm, RailwayGraph graph)`.
*   **Añadidos**:
    ```java
    void onForkEntered(ForkRailTrack fork);
    void onForkExited(ForkRailTrack fork);
    ```

### Integración en `TrainMovementManager` (Disparadores de Evento)

Durante la fase de movimiento del tren en [TrainMovementManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainMovementManager.java), se invoca de forma directa a la interfaz de seguridad:

*   **Al entrar la locomotora (cabeza)** al desvío:
    ```java
    if (headNextTrack instanceof ForkRailTrack fork) {
        fork.onEnterTrain(train);
        if (train.getSafetyManager() != null) {
            train.getSafetyManager().onForkEntered(fork);
        }
    }
    ```
*   **Al salir el último vagón (cola)** del desvío:
    ```java
    if (lastLinkerTrack instanceof ForkRailTrack fork) {
        fork.onExitTrain(train);
        if (train.getSafetyManager() != null) {
            train.getSafetyManager().onForkExited(fork);
        }
    }
    ```

### Implementación del Flujo Reactivo en `TrainSafetyManager`

#### A. Entrada al Desvío (`onForkEntered`)
Cuando la cabeza del tren pisa el desvío, realiza la transición del segmento lógico y pre-reserva el siguiente segmento en el sentido de la marcha de forma preventiva:

```java
@Override
public void onForkEntered(ForkRailTrack fork) {
    BlockManager bm = this.train.getModel().getBlockManager();
    RailwayGraph graph = this.train.getModel().getRailwayGraph();
    Linker head = train.getPhysicalFront();
    if (head == null || !(head.getTrack() instanceof RailTrack)) return;

    // Transicionar al segmento objetivo (que ya pre-bloqueamos anteriormente)
    Segment newSegment = nextSegment;
    if (newSegment == null) {
        newSegment = graph.getSegment((RailTrack) head.getTrack());
    }

    if (newSegment != null && !newSegment.equals(currentSegment)) {
        currentSegment = newSegment;
        isWaitingForBlock = false;
        
        // Failsafe: Asegurar propiedad del segmento actual
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean entryLocked = bm.tryLock(train, currentSegment);
            if (!entryLocked) {
                log.error("Train {} entered segment {} without lock! Emergency stop.", train.getId(), currentSegment.getId());
                train.movementManager.forceEmergencyStop();
                return;
            }
        }
        
        // Pre-bloquear el siguiente segmento en el itinerario
        nextSegment = findNextSegment(head, graph);
        if (nextSegment != null && !nextSegment.equals(currentSegment)) {
            boolean locked = bm.tryLock(train, nextSegment);
            if (!locked) {
                locked = tryAlternativeSegment(this.train.getModel());
            }
            if (locked) {
                isWaitingForBlock = false;
            } else {
                if (train.isAutoMode()) {
                    train.movementManager.initiateBraking();
                } else {
                    isWaitingForBlock = false;
                }
            }
        }
    }
}
```

#### B. Salida del Desvío (`onForkExited`)
Cuando la cola sale del desvío, se determina de forma determinista y topológica el segmento que se ha despejado, liberándolo inmediatamente sin realizar ningún bucle ni comprobar otros vagones:

```java
@Override
public void onForkExited(ForkRailTrack fork) {
    BlockManager bm = this.train.getModel().getBlockManager();
    RailwayGraph graph = this.train.getModel().getRailwayGraph();
    
    // 1. Obtener el RailNode asociado
    RailNode node = graph.getSegment(fork).getPorts().getFirst().getNode();
    
    // 2. Determinar por qué puerto del nodo está saliendo la cola (dirección del movimiento)
    Port exitPort = obtenerPuertoSalidaDeCola(fork, node); 
    
    // 3. Determinar el puerto despejado por simetría de la aguja física
    PortType clearedType;
    if (exitPort.getType() == PortType.A || exitPort.getType() == PortType.B) {
        clearedType = PortType.TRUNK;
    } else {
        clearedType = fork.isUsingAlternativeRoute() ? PortType.B : PortType.A;
    }
    Port clearedPort = node.getPortByType(clearedType);
    
    // 4. Liberar el segmento despejado
    Segment clearedSegment = graph.getSegment(clearedPort);
    if (clearedSegment != null) {
        bm.release(train, clearedSegment);
        log.info("Train {} released segment {} deterministically on fork exit", train.getId(), clearedSegment.getId());
    }
}
```

## Plan de Implementación por Fases

Para acometer esta refactorización de forma segura y controlada sin generar errores de compilación masivos, se define la siguiente secuencia de ejecución incremental:

### Fase 1: Estructura del Grafo y Coexistencia (Sin romper nada)
1. **Crear nuevos tipos lógicos**: Implementar las interfaces `Port` y `PortType` (TRUNK, A, B) y su implementación `PortImpl` en el paquete `letrain.segments`. Conservar intacta la interfaz `PathStep` y su implementación `PathStepImpl`.
2. **Duplicar interfaces en Segment y RailwayGraph**: Añadir nuevos métodos basados en `Port` a las interfaces `Segment` y `RailwayGraph` y sus respectivas implementaciones. Marcar todos los métodos antiguos basados en `PathStep` como `@Deprecated`.
3. **Mapear en TopologyServiceImpl**: Adaptar el motor de descubrimiento para que construya y asocie tanto las referencias a los puertos lógicos nuevos como los pasos lógicos antiguos.
*   *Criterio de Aceptación*: El código compila completamente y la suite de tests existente (`mvn clean test`) pasa al 100% en verde.

### Fase 2: Migración de la Navegación (Pathfinder y Autopilot)
1. **Refactorizar AStarPathfinder**: Modificar el algoritmo de búsqueda A* para que consuma y navegue utilizando `Port` y sus tipos `PortType` en lugar de `PathStep`.
2. **Refactorizar AutoPilotImpl**: Cambiar las llamadas de conmutación de desvíos en el guiado automático para que invoquen directamente al nodo con `node.setRoute(entryPort, exitPort)`, eliminando dependencias de comparaciones de direcciones cardinales físicas.
3. **Migrar Tests de Navegación**: Actualizar las aserciones y clases mock en `SegmentPathfinderTest` y `AutoPilotIntegrationTest` para ajustarlas a la interfaz de puertos.
*   *Criterio de Aceptación*: La navegación y guiado automático compila y todas sus pruebas pasan satisfactoriamente.

### Fase 3: Transición Reactiva en el Sistema de Seguridad
1. **Añadir Eventos Reactivos a TrainSafetyManager**: Declarar e implementar los métodos reactivos `onForkEntered(ForkRailTrack fork)` y `onForkExited(ForkRailTrack fork)` en el safety manager de los trenes.
2. **Eliminar el Polling en ticks**: Retirar la invocación a `onTrackEntered(Track track)` y `releaseOldSegments(...)` de la física de movimiento, y borrar sus cuerpos de implementación.
3. **Conectar disparadores directos**: Modificar `TrainMovementManager` para que llame directamente a `onForkEntered` al entrar la locomotora a un desvío y a `onForkExited` al salir completamente el último vagón.
*   *Criterio de Aceptación*: El simulador gestiona la reserva y liberación de bloques de forma puramente reactiva en tránsito, verificado mediante pruebas manuales y suite de tests.

### Fase 4: Limpieza y Eliminación de Código Obsoleto
1. **Eliminación definitiva**: Borrar del repositorio las clases `PathStep` y `PathStepImpl`.
2. **Retirar Deprecados**: Eliminar de todas las interfaces lógicas y clases físicas (`Segment`, `RailwayGraph`, `TopologyService`) los métodos antiguos marcados como `@Deprecated` en la Fase 1.
3. **Refactorización de Tests Residuales**: Adaptar y depurar cualquier referencia unitaria o de integración residual en la suite de pruebas.
*   *Criterio de Aceptación*: La suite completa de tests de Maven (`mvn clean test`) pasa completamente limpia y en verde, con el codebase 100% libre de polling e interfaces antiguas.

---
*Última actualización: 2026-06-09*
