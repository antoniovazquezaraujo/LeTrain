# Informe de Simplificación del Sistema de Bloqueos

De acuerdo con tus indicaciones para reiniciar y simplificar la lógica de bloqueos aplicando estrictamente las 6 reglas que has descrito, aquí tienes el análisis de qué archivos habría que modificar y cómo hacerlo.

## 1. `TrainSafetyManager.java` (`letrain/vehicle/rail/impl/TrainSafetyManager.java`)

Este será el archivo que sufrirá la mayor transformación y limpieza, ya que centraliza la lógica de seguridad y cantones.

### Cambios a realizar:

*   **Simplificación de `onSegmentEntered(Segment newSegment)`:**
    *   **Regla 1 y 2 (Bloqueo del segmento siguiente):** Al confirmar la entrada en un segmento `B`, se identificará el segmento `C` y se intentará hacer un `tryLock()`. Si el bloqueo falla:
        *   Se llamará a `tryAlternativeSegment(model)`. Habrá que asegurar que este método verifique estrictamente que los dos ramales divergen desde donde estamos y convergen al mismo nodo (esto último ya parece hacerlo parcialmente), y que ambos están libres de paradas del itinerario. Si esto tiene éxito, se cambia la ruta/aguja.
        *   Si falla o no es un apartadero (ej. es una convergencia), simplemente se llama a `train.getMovementManager().initiateBraking()` y se pone `isWaitingForBlock = true`.
    *   **Reglas 3 y 4 (Invasión por inercia):** Actualmente, si un tren entra a un segmento que ya estaba ocupado, lanza un `forceEmergencyStop()` para todos los implicados. Con tu corrección, si falla el `tryLock()` del segmento actual al entrar (porque la inercia lo metió), se debe forzar el freno de emergencia (`train.getMovementManager().forceEmergencyStop()`) **y además** iterar por los dueños del segmento (`bm.getOwners()`) para poner a TODOS los trenes implicados (el invasor y los dueños) en modo manual (**`train.setAutoMode(false)`**).
    *   **Regla 5 (Liberación al salir de un segmento):** Tal como indicas, el segmento NO se libera cuando la locomotora sale de él, sino cuando sale el último vehículo de la composición. Esto significa que la lógica actual que rastrea la cola del tren (a través de eventos como cuando el último vagón abandona el cantón físico) **se mantiene igual**. Se seguirá llamando a `bm.release(train, abandonedSegment)` solo cuando el final del tren desocupe el bloque por completo.

*   **Revisión de `onBlockReleased()`:**
    *   **Regla 6 (Aviso tras desbloqueo):** La lógica base ya existe. Cuando se reciba el aviso de que un segmento se ha liberado, si el tren estaba en `isWaitingForBlock == true`, se vuelve a intentar `tryLock(nextSegment)`. Si tiene éxito, se restaura la velocidad (`train.restoreSpeed()`) y se apaga el flag de espera.

*   **Limpieza general:**
    *   Métodos como `onForkEntered`, `onForkExited` y otras comprobaciones topológicas redundantes que intentan adivinar la posición segura del tren de forma muy defensiva se podrán eliminar o reducir a su mínima expresión, delegando todo al flujo directo de `onSegmentEntered` y eventos de cola.

---

## 2. `BlockManagerImpl.java` (`letrain/segments/impl/BlockManagerImpl.java`)

Aunque la gestión de mapas concurrentes de bloqueos es funcional, puede simplificarse para adaptarse a tu nuevo paradigma.

### Cambios a realizar:

*   **Eliminación de la lógica extra (Shunting):** Actualmente existe un `tryShuntingLock` que permite coexistencia bajo ciertas condiciones. Basado en tus nuevas reglas (si hay invasión, pasan a manual, y solo puede haber apartaderos limpios), toda la lógica de "shunting" podría eliminarse temporal o permanentemente para mantener el sistema lo más estricto y simple posible. Solo quedaría el `tryLock` exclusivo puro.
*   **Avisos dirigidos (Listeners):** Actualmente usa un único `onReleaseListener` (Consumer) general. Para que la **Regla 6** sea eficiente (avisar a un tren en espera de que vuelva a intentar bloquear el segmento que quería), vendría bien que el sistema de notificación despierte a los trenes concretos que miran hacia ese bloque en lugar de un broadcast general, o al menos que el `BlockManager` maneje colas de espera por segmento.

---

## 3. Interfaces y Clases de UI / Autopiloto (Posibles efectos colaterales menores)

*   **`Train.java` o `AutoPilot.java`:** Habrá que asegurar que cuando hagamos el paso a modo manual en una invasión por inercia (Regla 3 y 4), se notifique correctamente al UI o se limpie la ruta de autopiloto temporalmente para que de verdad "espere instrucciones del usuario".
*   **Agujas (Forks):** Si el tren logra reservar el ramal alternativo del apartadero (Regla 2), hay que asegurarse de que la orden del cambio de aguja se ejecute físicamente antes de que pase el tren (actualmente lo maneja modificando el itinerario del `AutoPilot`, lo cual debería seguir sirviendo si se simplifica adecuadamente).
