[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-003: Topología del Grafo y Motor de Rutas

## Estado: Implementación validada

## Contexto
El sistema actual evoluciona hacia un modelo de explotación ferroviaria real, donde los conceptos de **itinerario (route)** y **cantón de bloqueo (block section)** se gestionan de forma integrada con la señalización y los sistemas de seguridad (enclavamientos).

## Objetivos
- **Independencia funcional**: Todo lo relacionado con bloqueos e itinerarios reside en el paquete `letrain.railway`, desacoplado del motor físico.
- **Abstracción topológica**: Separar la representación física (raíles en rejilla) de la representación lógica (grafo de nodos y puertos).
- **Descubrimiento Automático**: Motor de rastreo de vías (wire-tracing) que construye el grafo dinámicamente.
- **Seguridad Determinista**: Establecer rutas basadas en la orientación física real del tren para evitar trayectorias imposibles.

## Decisiones de Diseño

### 1. El Grafo Ferroviario (RailwayGraph)
En lugar de coordenadas X e Y, el sistema trabaja sobre un grafo de conexiones lógicas. 
- **RailNodeLink**: Representa un "enchufe" específico (Nodo + Puerto).
- **RailwayGraph**: Mapea cada puerto de salida con su correspondiente puerto de llegada en el siguiente nodo.
- **Identificación Robusta**: Se ha implementado `findNodeByTrack`, que localiza nodos por el objeto `Track` físico, eliminando errores de precisión decimal en las coordenadas.

### 2. Motor de Rutas (PathResolver A*)
El algoritmo A* ha sido mejorado para ser consciente de la **orientación inicial**:
- Solo explora rutas que comiencen en la dirección en la que mira la locomotora.
- Si el tren entra en un nodo por el puerto X, solo puede salir por los puertos compatibles definidos en `getAvailableExitPorts`.

### 3. Tránsito por Nodos (PathStep)
Un camino (`Path`) se define por cómo se atraviesan los nodos:
- **Puerto de entrada**: Por dónde llega el tren.
- **Puerto de salida**: Por dónde abandona el nodo.
- El sistema utiliza esta información para **orientar proactivamente** los desvíos (Forks) antes de que el tren llegue físicamente a ellos.

## Definiciones Técnicas (Implementadas)

```java
// Representa un punto de decisión o frontera (desvíos, estaciones, sensores).
interface RailNode {
    Dir getDirPort(int port);   // Dirección geográfica de un puerto.
    int getPortDir(Dir dir);    // Puerto asociado a una dirección de entrada/salida.
    void orient(int entry, int exit); // Configura el nodo para el tránsito.
}

// Punto de enlace único: un nodo y uno de sus puertos específicos.
class RailNodeLink {
    RailNode node;
    int port;
}

// Define el paso por un nodo: entrada y salida.
class PathStep {
    RailNode node;
    int entryPort;
    int exitPort;
}
```

## Próximos Pasos
1.  **Fase 2: Plantillas de Itinerarios**: Permitir que varios trenes compartan la misma lista de misiones (ya habilitado por la inmutabilidad de misiones).
2.  **Señalización Avanzada**: Integrar semáforos físicos con los bloqueos de puerto automáticos.

---
*Última actualización: 2026-04-19*
