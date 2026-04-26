[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-004: Gestión de Itinerarios y Control Automático (AutoPilot)

## Estado: Implementación validada (Despacho Explícito)

## Contexto
Para automatizar la red ferroviaria, se introduce una capa de inteligencia desacoplada del tren físico. La entidad **AutoPilot** actúa como un "conductor virtual" que toma decisiones basadas en un itinerario y en el estado de la red, comunicándose con el tren mediante eventos y órdenes de velocidad.

## El Componente AutoPilot
El `AutoPilot` es el cerebro que orquesta la conducción automática. Sus responsabilidades son:
1.  **Gestión del Itinerario**: Mantiene la lista de `MissionSteps` y coordina con el [[ADR-003-Segments|PathResolver]] para obtener rutas físicas (`Path`).
2. **Ejecución del Protocolo de Seguridad**: Implementa las reglas de bloqueo por segmentos del [[ADR-005-Block-Segments|ADR-005]] para evitar colisiones.
3.  **Interfaz de Eventos**: Implementa `TrainEventListener` para reaccionar en tiempo real a la entrada y salida del tren de los nodos ferroviarios.

## Modelo de Despacho Explícito (Explicit Dispatch)
A diferencia de un sistema de navegación libre, LeTrain utiliza un modelo determinista donde el jugador tiene la última palabra sobre la intención física:
- **Enganche Asistido (Engagement)**: El modo automático solo se activa (tecla `a`) si el sistema encuentra un camino válido hacia alguna estación del itinerario **respetando la orientación física actual** de la locomotora.
- **Validación de Sentido**: Si el tren mira hacia una vía muerta o una ruta bloqueada, el sistema deniega el enganche con un mensaje de "CAMINO NO ENCONTRADO".
- **Retorno al Manual**: El `AutoPilot` se desactiva inmediatamente si el jugador toma el control manual (teclas de velocidad) o si se pierde la integridad de la ruta ("RUTA PERDIDA O BLOQUEADA").

## Estructura de la Misión (Flags de Acción)
Un itinerario es una lista **inmutable** de **`MissionStep`**. El `AutoPilot` utiliza un **cursor lógico bidireccional** (`itinerarySense`) para recorrerla sin modificar la lista original (permitiendo itinerarios compartidos).

Flags de acción al llegar a un nodo:
1.  **WAIT**: El tren frena hasta detenerse y espera un tiempo técnico (por defecto 15s).
2.  **TRADE**: Inicia el proceso de carga/descarga de recursos.
3.  **REVERSE**: Tras las acciones, el tren realiza una secuencia atómica:
    - Invierte su marcha física (`toggleReversed`).
    - Invierte su sentido de avance lógico (`itinerarySense` de 1 a -1 o viceversa).
    - Recalcula la ruta estrictamente en el nuevo sentido hacia el siguiente objetivo.
4.  **STOP**: Desactiva el `AutoPilot` al llegar, entregando el mando al jugador.
5.  **PASS (Sin flags)**: El tren transita por el nodo sin detenerse.

## Lógica de Navegación y Orientación

### 1. El Itinerario "Péndulo"
El cursor bidireccional permite que un itinerario lineal actúe como un bucle infinito de ida y vuelta. La combinación de `REVERSE` en los extremos garantiza la oscilación eterna del tren entre dos puntos sin intervención manual.

### 2. Orientación Bidireccional de Desvíos
Los desvíos (`ForkRailNode`) se orientan proactivamente considerando tanto el puerto de entrada como el de salida (`orient(entry, exit)`). Esto asegura la continuidad física del raíl incluso cuando el tren circula "de espaldas" o entra desde una ramificación hacia el tronco común.

### 3. Sincronización de Linkers
Tras cualquier inversión (REVERSE o manual), el sistema fuerza un refresco de la dirección de todos los componentes del tren (`refreshLinkersDirection`), asegurando que los vagones y la locomotora estén alineados con el nuevo vector de empuje antes de que el piloto automático evalúe la seguridad de la vía.

---
*Última actualización: 2026-04-19*
