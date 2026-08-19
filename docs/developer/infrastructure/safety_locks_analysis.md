# Informe de Análisis: Llamadas a `acquireInitialLocks`

Este informe analiza en detalle los 7 puntos del código de producción de **LeTrain** desde donde se invoca el método `acquireInitialLocks()` de la interfaz [TrainSafetyManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/TrainSafetyManager.java). El objetivo es identificar redundancias, antipatrones de diseño y posibles fallos de coherencia.

---

## Resumen de las 7 Llamadas

El método `acquireInitialLocks()` está diseñado para reservar de forma reactiva el segmento actual y el siguiente cuando un tren va a iniciar su marcha o cambia su configuración.

A continuación se detalla cada una de las 7 llamadas agrupadas por componentes:

### 1. `TrainActionManager` (2 llamadas)

*   **Llamada 1: Reanudación programada**
    *   **Archivo/Línea:** [TrainActionManager.java:L104](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/TrainActionManager.java#L104)
    *   **Contexto:** Dentro de `scheduleResume(int ticks)`, al expirar un temporizador de espera (por ejemplo, en estaciones o waypoints) para reanudar la marcha.
    *   **Código:**
        ```java
        train.getModel().getScheduler().schedule(ticks, () -> {
            resumeWaiting();
            train.getSafetyManager().acquireInitialLocks();
        });
        ```

*   **Llamada 2: Método privado auxiliar no utilizado**
    *   **Archivo/Línea:** [TrainActionManager.java:L127](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/TrainActionManager.java#L127)
    *   **Contexto:** Dentro del método privado auxiliar local `acquireInitialLocks()` de la misma clase.
    *   **Código:**
        ```java
        private void acquireInitialLocks() {
            // ... (lógica de notificación al autopilot) ...
            this.train.getSafetyManager().acquireInitialLocks();
        }
        ```

> [!WARNING]
> **Antipatrón de Código Muerto e Inconsistencia**:
> El método local `acquireInitialLocks()` de `TrainActionManager` **nunca es invocado** desde ningún punto del archivo. Es código muerto. 
> Además, este método local tiene lógica adicional: notifica al autopilot sobre la entrada al segmento actual (`onSegmentEntered`) para que recalcule rutas y alinee desvíos. Al llamarse directamente a `train.getSafetyManager().acquireInitialLocks()` en `scheduleResume` (Llamada 1) en lugar de usar el wrapper privado local, **se salta la notificación al piloto automático**, lo que podría ocasionar que el piloto no alinee las agujas o no tenga la ruta actualizada al reanudar la marcha.

---

### 2. `Model` (1 llamada)

*   **Llamada 3: Carga de partida**
    *   **Archivo/Línea:** [Model.java:L257](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/Model.java#L257)
    *   **Contexto:** En el proceso de inicialización tras deserializar/cargar una partida guardada (`postLoadInit()`).
    *   **Código:**
        ```java
        // Pass 2: Acquire initial lookahead locks for all active autopilot trains
        for (Locomotive loco : locomotives) {
            Train train = loco.getTrain();
            if (train != null && train.isAutoMode()) {
                train.actionManager.checkWaypointArrival();
                train.getSafetyManager().acquireInitialLocks();
            }
        }
        ```

> [!NOTE]
> **Correcto y Justificado**:
> La inicialización de la carga se hace en dos pasadas muy claras: en la primera pasada, todos los trenes declaran/reclaman los bloques que están ocupando físicamente en el mapa (`claimOccupiedSegments()`). En la segunda pasada, los trenes que están en piloto automático solicitan sus bloqueos iniciales predictivos (`acquireInitialLocks()`). Esto previene condiciones de carrera donde un tren intente reservar de forma predictiva un bloque que otro tren está ocupando físicamente pero que aún no ha reclamado debido a que no ha sido inicializado.

---

### 3. `Locomotive` (1 llamada)

*   **Llamada 4: Incremento de velocidad objetivo en locomotora**
    *   **Archivo/Línea:** [Locomotive.java:L281](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Locomotive.java#L281)
    *   **Contexto:** Dentro de `setTargetSpeed(int speed)` cuando la velocidad objetivo de la locomotora aumenta desde cero (`oldSpeed == 0 && targetSpeed > 0`).
    *   **Código:**
        ```java
        if (oldSpeed == 0) {
            log.info("Locomotive {}: target speed increased from 0. Acquiring initial locks.", id);
            Train train = getTrain();
            train.actionManager.checkWaypointArrival();
            train.getSafetyManager().acquireInitialLocks();
        }
        ```

> [!CAUTION]
> **Antipatrón de Acoplamiento Fuerte y Responsabilidad Invertida**:
> La locomotora es una entidad física (un componente tractor de un tren). No debería interactuar directamente con elementos lógicos de alto nivel de control del tren como el gestor de acciones (`TrainActionManager#checkWaypointArrival()`) o el gestor de seguridad general (`TrainSafetyManager#acquireInitialLocks()`). Esto introduce un acoplamiento innecesario.
> Además, este diseño causa **redundancia de llamadas** cuando la velocidad se cambia a través de la entidad controladora `Train`.

---

### 4. `Train` (3 llamadas)

*   **Llamada 5: Activación de Autopiloto**
    *   **Archivo/Línea:** [Train.java:L148](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java#L148)
    *   **Contexto:** Al alternar/activar el modo automático (`toggleAutoMode()`).
    *   **Código:**
        ```java
        if (activated) {
            this.actionManager.checkWaypointArrival();
            this.safetyManager.acquireInitialLocks();
            this.actionManager.checkWaypointArrival();
        }
        ```

> [!WARNING]
> **Antipatrón de Redundancia Defensiva / Programación por Coincidencia**:
> Invoca a `this.actionManager.checkWaypointArrival()` de forma idéntica inmediatamente antes e inmediatamente después de `acquireInitialLocks()`. Esto es un síntoma de confusión en el flujo de control, donde el desarrollador no tenía claro en qué orden debían ejecutarse estas llamadas, por lo que las duplicó "por si acaso".

*   **Llamada 6: Establecer velocidad en el tren**
    *   **Archivo/Línea:** [Train.java:L223](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java#L223)
    *   **Contexto:** En `setSpeed(int speed)` si la velocidad objetivo pasa a ser mayor que cero.
    *   **Código:**
        ```java
        public void setSpeed(int speed) {
            Tractor speedLinker = getDirectorLinker();
            if (speedLinker != null) {
                this.setSavedSpeedBeforeReverse(-1);
                speedLinker.setSpeed(speed);
                if (speed > 0 && getModel() != null) {
                    getSafetyManager().acquireInitialLocks();
                }
            }
        }
        ```

> [!WARNING]
> **Ejecución Duplicada e Innecesaria**:
> Al invocar a `speedLinker.setSpeed(speed)`, si el linker director es una locomotora, esta llamará a `setTargetSpeed()`. Si la locomotora estaba parada, disparará la **Llamada 4** (intentando adquirir bloqueos). Inmediatamente después, el flujo regresa a `Train.setSpeed()` y vuelve a ejecutar `getSafetyManager().acquireInitialLocks()` (Llamada 6). El mismo tren intenta reservar sus bloqueos dos veces seguidas en el mismo hilo de ejecución, lo cual es ineficiente y denota falta de coordinación.

*   **Llamada 7: Rebind/Acoplamiento físico**
    *   **Archivo/Línea:** [Train.java:L323](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java#L323)
    *   **Contexto:** Dentro de `rebind()`, que actualiza la composición física del tren (acoplado/desacoplado de vagones) y restablece sus referencias al modelo.
    *   **Código:**
        ```java
        public void rebind() {
            if (model == null) { ... }
            safetyManager.claimOccupiedSegments();
            if (isAutoMode()) {
                safetyManager.acquireInitialLocks();
            }
        }
        ```

> [!NOTE]
> **Coherente y Justificado**:
> Tras una modificación estructural en el tren (por ejemplo, al desacoplar vagones o unir trenes), es fundamental que el tren vuelva a asegurar los cantones físicos que ocupa y, si está en piloto automático, recalcule y adquiera los bloqueos preventivos iniciales para continuar su marcha.

---

## Conclusión

El sistema funciona, pero está notablemente **confuso y sobre-acoplado** en lo relativo a la gestión de los bloqueos de seguridad al iniciar la marcha:

1. **Inconsistencia de notificación de Autopiloto**: El código muerto en `TrainActionManager` oculta el hecho de que en ciertas reanudaciones (`scheduleResume`) nos saltamos la notificación de segmento al piloto automático.
2. **Redundancia Defensiva**: La llamada duplicada a `checkWaypointArrival` en `toggleAutoMode()` muestra falta de certeza sobre el ciclo de vida del estado del tren.
3. **Flujo de Ejecución Duplicado**: Al cambiar la velocidad del tren mediante `Train.setSpeed()`, se dispara tanto la comprobación interna de la `Locomotive` como la del `Train`, ejecutando de forma secuencial dos veces seguidas el proceso de adquisición de bloqueos.
4. **Fuerte Acoplamiento**: La clase física `Locomotive` conoce y gestiona lógicas de alto nivel correspondientes al control global del tren (`TrainSafetyManager` y `TrainActionManager`).

---

## Corrección Propuesta y Puntos de Invocación

Para solucionar estos problemas de acoplamiento, duplicidad y consistencia, se propone centralizar la responsabilidad en la clase controladora principal, [Train](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java), eliminando accesos directos desde las locomotoras o capas inferiores.

### 1. Principio de Centralización en `Train`
La clase [Locomotive](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Locomotive.java) **no debe** conocer a [TrainSafetyManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/TrainSafetyManager.java) ni a [TrainActionManager](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/TrainActionManager.java). Su única función debe ser reaccionar a cambios en variables de potencia/física. 
Cualquier evento de cambio de velocidad física que requiera comprobaciones de seguridad debe propagarse hacia arriba o ser controlado directamente por el contenedor [Train].

---

### 2. Momentos y Puntos de Invocación Correctos

A continuación, se enumeran los únicos momentos en los que se debe invocar a `acquireInitialLocks()` y el flujo desde donde debe llamarse:

| # | Momento del Proceso | Origen de la Invocación | Razón y Flujo de Control |
|---|---------------------|-------------------------|--------------------------|
| **1** | **Arranque manual o cambio de velocidad a > 0** | [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java) en `setSpeed(speed)` | Cuando el tren estaba detenido (velocidad actual `0`) y se configura una velocidad mayor que cero. El flujo debe ser:<br>1. El `Train` cambia la velocidad física delegando al linker (`speedLinker.setSpeed`).<br>2. Se elimina la lógica de llamadas de seguridad dentro de `Locomotive.setTargetSpeed` (rompiendo el acoplamiento).<br>3. El `Train` invoca a `safetyManager.acquireInitialLocks()`. |
| **2** | **Activación del Piloto Automático** | [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java) en `toggleAutoMode()` / `setAutoMode(true)` | Cuando el tren pasa de manual a piloto automático y tiene un itinerario cargado. El flujo debe ser:<br>1. Se activa el piloto automático (`autopilot.activate()`).<br>2. Se evalúa el waypoint actual (`actionManager.checkWaypointArrival()`).<br>3. Se adquieren los bloqueos iniciales predictivos del autopilot (`safetyManager.acquireInitialLocks()`).<br>*(Se elimina la segunda llamada redundante a `checkWaypointArrival`)*. |
| **3** | **Reanudación de marcha tras parada programada (Waypoint/Estación)** | [TrainActionManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/TrainActionManager.java) mediante el wrapper privado | Cuando el temporizador programado por el scheduler expira en `scheduleResume(ticks)`. El flujo debe ser:<br>1. En lugar de llamar directamente a `train.getSafetyManager().acquireInitialLocks()`, se debe invocar al método local privado `acquireInitialLocks()` (que actualmente es código muerto).<br>2. Este método privado notifica la entrada al segmento al piloto automático (`autopilot.onSegmentEntered()`).<br>3. Finalmente, el wrapper privado llama a `train.getSafetyManager().acquireInitialLocks()`. |
| **4** | **Reconfiguración estructural del tren (Rebind)** | [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/Train.java) en `rebind()` | Al acoplar o desacoplar vagones o locomotoras. El flujo debe ser:<br>1. El `Train` actualiza su estructura y llama a `safetyManager.claimOccupiedSegments()`.<br>2. Si el tren está en modo autopilot, se invoca a `safetyManager.acquireInitialLocks()`. *(Este flujo ya es correcto y limpio)*. |
| **5** | **Carga de partida guardada (Post-Load)** | [Model.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/Model.java) en `postLoadInit()` (Paso 2) | En la inicialización del sistema tras leer de disco. La orquestación por parte del `Model` es necesaria para evitar condiciones de carrera entre trenes. El flujo debe ser:<br>1. Paso 1: Todos los trenes reclaman ocupación física.<br>2. Paso 2: El `Model` itera los trenes en piloto automático, valida waypoints e invoca a `train.getSafetyManager().acquireInitialLocks()`. *(Este flujo ya es correcto y limpio)*. |

Este diseño propuesto elimina completamente:
* El código muerto en `TrainActionManager`.
* La doble llamada en cambios de velocidad de locomotora/tren.
* El acoplamiento cíclico/fuerte entre `Locomotive` y los gestores de lógica del tren (`TrainSafetyManager` y `TrainActionManager`).
* La redundancia defensiva en `toggleAutoMode`.
