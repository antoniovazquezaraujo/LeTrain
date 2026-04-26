# ADR-000: Sistema de Guiado Automático (Fase 1: Guiado Topológico)

## Estado
Propuesto

## Contexto
Se requiere un sistema que permita a un tren alcanzar un destino de forma autónoma basándose en la topología de la red ferroviaria. Se descarta el seguimiento baldosa a baldosa en favor de un modelo de grafos.

## Decisión: Abstracción Topológica por Segmentos

### 1. Entidades Mínimas
- **RailNode**: Punto de decisión o frontera (Forks, Topes de vía). Gestiona su propia lista de `PathSteps` de salida.
- **PathStep**: Una intención o decisión en un nodo: `(RailNode, Dir)`.
- **Segment**: Conexión física única entre dos `RailNode`. Se define mediante un `Pair<PathStep, PathStep>` que representa sus dos extremos.

### 2. Interfaz del Grafo (`RailwayGraph`)
El sistema se basará en una interfaz que gestione la conectividad sin conocer los detalles físicos de los raíles.

```java
public interface RailwayGraph {
    /**
     * Dado un PathStep, devuelve el segmento al que pertenece.
     */
    Segment getSegment(PathStep step);

    /**
     * Dado un paso actual, devuelve los posibles pasos siguientes 
     * al final del segmento. Devuelve null si es fin de vía.
     */
    List<PathStep> getNextSteps(PathStep current);

    /**
     * Encuentra la secuencia de segmentos que conectan dos segmentos dados.
     */
    List<Segment> findPath(Segment start, Segment end);
}
```

### 3. Lógica de Implementación Simple
- El `RailwayGraph` mantendrá un mapeo `Map<PathStep, Segment>` y un registro de qué segmentos llegan a cada `Node`.
- Para encontrar el "Siguiente Paso", el grafo identifica el nodo opuesto en el segmento y consulta los pasos de salida de dicho nodo.

## Consecuencias
- **Desacoplamiento:** La lógica de navegación es independiente de si la vía es curva, recta o un túnel.
- **Eficiencia:** Las búsquedas de ruta se realizan sobre un grafo de nodos reducido.
- **Robustez:** La integridad se mantiene siempre que los `Segments` y sus `Nodes` estén correctamente vinculados.
