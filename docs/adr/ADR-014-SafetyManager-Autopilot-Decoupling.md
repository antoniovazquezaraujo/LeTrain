# ADR-014: Desacoplamiento de TrainSafetyManager y AutoPilot a través de Train

## Estado: APROBADO

## Contexto

En el diseño original, el gestor de seguridad del tren (`TrainSafetyManager`) notificaba directamente al piloto automático (`AutoPilot`) cuando el tren entraba físicamente en un nuevo segmento o cantón. Esto introducía un acoplamiento bidireccional innecesario y confuso entre las capas de seguridad de bloques (bloqueo y reserva de cantones) y la capa de navegación del piloto automático (trazado de rutas y orientación de desvíos).

Además, para cumplir con el principio de responsabilidad única, la clase `Train` debe actuar como el orquestador central y punto de entrada para los eventos del tren, controlando el flujo y la secuencia exacta de llamadas a las demás subclases auxiliares.

## Decisión

Se toman las siguientes medidas de refactorización y diseño:

1. **Desacoplamiento Completo**:
   - Se eliminan todas las referencias directas al `AutoPilot` dentro de `TrainSafetyManager`.
   - `TrainSafetyManager` ya no es responsable de notificar eventos de entrada o bloqueo de segmento a `AutoPilot`.

2. **Orquestación en `Train`**:
   - La clase `Train` asume de forma centralizada la responsabilidad de recibir los eventos del motor físico (`notifySegmentEntered`, `notifyEnterSensor`, `acquireInitialLocks`) y distribuirlos a las capas lógicas en la secuencia correcta:
     1. **Waypoint Reached Check**: Se comprueba si el tren ha alcanzado el waypoint actual del itinerario (`checkWaypointArrival`) y se ejecutan sus comandos DSL asociados (cambios de velocidad, espera con temporizadores o inversión de marcha).
     2. **Navigation Updates**: Se notifica al `AutoPilot` (`onSegmentEntered`) para que realice el cálculo de ruta al siguiente destino y oriente los desvíos correspondientes.
     3. **Safety Locks**: Se notifica al `TrainSafetyManager` (`onSegmentEntered` o `acquireInitialLocks`) para que bloquee el cantón actual, intente reservar el cantón subsiguiente, y libere los cantones abandonados por la cola del tren.
   - Asimismo, se traslada el método `forceEmergencyStop()` a `Train`. Cuando ocurre un conflicto de seguridad en el cantón, `Train` desactiva el piloto automático y frena las locomotoras física y directamente, mientras que el `TrainSafetyManager` únicamente restablece su estado de bloqueo interno (a través de `onEmergencyStop()`).

3. **Limpieza del Estado de Ruta**:
   - Se añade el método `clearRoute()` en la interfaz `AutoPilot` y su implementación en `AutoPilotImpl` para resetear el caché de ruta del piloto automático al avanzar al siguiente waypoint. De esta forma, el piloto automático recalcula de manera reactiva la ruta hacia el nuevo destino en la siguiente transición de segmento.

## Consecuencias

- **Responsabilidades Claras**: El piloto automático se limita a la navegación e itinerarios, el gestor de seguridad a la reserva y física de bloqueos de vía, y el tren orquesta la interacción de ambos.
- **Flujo de Ejecución Garantizado**: Al centralizarse en `Train`, el orden de prioridades (primero ejecutar los comandos de parada/espera al llegar a estación, luego cambiar de waypoint y recalcular la ruta, y por último bloquear el siguiente segmento) es 100% predecible y consistente.
- **Facilidad de Prueba**: Los tests de integración de itinerarios (`AutoPilotIntegrationTest`) pasan de forma consistente al no depender del orden incidental de notificaciones internas de seguridad.
