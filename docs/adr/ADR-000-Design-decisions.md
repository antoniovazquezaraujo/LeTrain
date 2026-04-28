# ADR-000: Sistema de Guiado Automático (Fase 1: Guiado Topológico)

## Estado
Implementado / En Evolución

## Contexto
Se requiere un sistema que permita a un tren alcanzar un destino de forma autónoma basándose en la topología de la red ferroviaria. Se descarta el seguimiento baldosa a baldosa en favor de un modelo de grafos lógicos que desacople la navegación de la física del raíl.

## Decisión: Abstracción Topológica por Segmentos

### 1. Entidades Fundamentales
- **RailNode**: Punto de decisión o frontera lógica. Se limita exclusivamente a **Forks** (desvíos) y **Topes de vía**. Es el "dueño" de la conectividad y define los límites de los segmentos.
- **PathStep**: La unidad mínima de intención. Combina un `RailNode` con una dirección (`Dir`) de salida.
- **Segment**: El tramo atómico de vía entre dos nodos. Es el contenedor de la propiedad física y los elementos operativos.

### 2. Interfaz del Grafo (`RailwayGraph`)
El `RailwayGraph` es la fuente de verdad para la navegación y el contexto operativo.

```java
public interface RailwayGraph {
    // --- Navegación ---
    Segment getSegment(PathStep step);
    List<PathStep> getNextSteps(PathStep current);
    List<Segment> findPath(Segment start, Segment end);

    // --- Contexto Operativo ---
    List<Station> getStations(Segment segment);
    List<Sensor> getSensors(Segment segment);
    Segment getSegment(RailTrack track);
}
```

### 3. Lógica de Descubrimiento
El `TopologyService` realiza un crawl (rastreo) recursivo del mapa físico para construir el grafo. Durante este proceso, asocia dinámicamente cada raíl, estación y sensor al `Segment` correspondiente.

## Consecuencias
- **Independencia del Motor Físico:** El buscador de rutas no necesita saber si la vía es curva o recta.
- **Soporte para Seguridad:** Los segmentos atómicos permiten implementar bloqueos robustos (ADR-005).
- **Visibilidad Operativa:** El sistema sabe exactamente qué estaciones hay "delante" en el grafo, permitiendo una planificación de paradas mucho más sencilla.

---
*Última actualización: 2026-04-28*
