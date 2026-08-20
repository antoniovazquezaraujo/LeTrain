[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-003: Topología del Grafo y Motor de Rutas

## Estado: SUPERADO por [[ADR-015-Abstraccion-Puertos-Nodos]]

## Contexto
El sistema actual evoluciona hacia un modelo de explotación ferroviaria real, donde los conceptos de **itinerario (route)** y **cantón de bloqueo (block section)** se gestionan de forma integrada con la señalización y los sistemas de seguridad.

## Objetivos
- **Independencia funcional**: Todo lo relacionado con la topología reside en el paquete `letrain.core.segments`.
- **Abstracción topológica**: Separar la representación física (raíles en rejilla) de la representación lógica (grafo de nodos y pasos).
- **Descubrimiento Automático**: Motor de rastreo de vías (wire-tracing) que construye el grafo dinámicamente (`TopologyService`).
- **Seguridad Determinista**: Establecer rutas basadas en la orientación física real del tren para evitar trayectorias imposibles.

## Decisiones de Diseño

### 1. El Grafo Ferroviario (RailwayGraph)
En lugar de solo coordenadas X e Y, el sistema trabaja sobre un grafo de conexiones lógicas. 
- **PathStep**: Representa una decisión o intención en un punto de la red (Nodo + Dirección Geográfica).
- **RailwayGraph**: Mapea la conectividad entre segmentos y nodos, permitiendo la navegación lógica.

### 2. Motor de Rutas (PathResolver A*)
El algoritmo A* es consciente de la **orientación inicial**:
- Solo explora rutas que comiencen en la dirección en la que mira la locomotora.
- Si el tren está en un nodo, solo puede salir por las direcciones compatibles definidas por el `RailMap`.

### 3. Tránsito por Nodos (PathStep)
Un camino (`Path`) se define por una secuencia de pasos por los nodos. El sistema utiliza esta información para **orientar proactivamente** los desvíos (Forks) antes de que el tren llegue físicamente a ellos.

## Definiciones Técnicas (Implementadas)

```java
// Representa un punto de decisión o frontera (desvíos, estaciones, sensores).
public interface RailNode {
    List<PathStep> getOutSteps(); // Pasos de salida disponibles desde este nodo.
    Track getTrack();             // Objeto de vía físico asociado.
}

// Una intención o decisión en un nodo: (RailNode, Dir).
public interface PathStep {
    RailNode getRailNode();
    Dir getDir();
}
```

## Próximos Pasos
1.  **Integración de Bloqueos**: Utilizar los segmentos del grafo para el sistema de seguridad (ver ADR-005).
2.  **Señalización Avanzada**: Integrar semáforos físicos con el estado de ocupación de los segmentos.

---
*Última actualización: 2026-04-19*
