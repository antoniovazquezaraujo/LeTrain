[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-005: Sistema de Seguridad y Ciclo de Vida de la Red

## 1. Contexto y Objetivos
Este documento define la arquitectura de seguridad de LeTrain. Tras varios intentos descartados que resultaron en bloqueos parciales e inconsistencias, se establece un modelo de **Segmentos Atómicos** y una gestión de estado **efímera**. El objetivo es garantizar que nunca haya dos trenes en el mismo espacio físico (salvo en maniobras controladas) y que los desvíos (Forks) estén siempre protegidos.

## 2. Definición de la Unidad Fundamental: El Segmento
Un **Segmento** es el tramo indivisible de vía que el sistema de seguridad protege como un todo. Es la unidad mínima de propiedad.
- **Fronteras Reales**: Un segmento nace y muere exclusivamente en puntos de decisión lógica: **Forks** (desvíos/agujas) o **Fines de vía** (topes).
- **Entidades Contenidas**: Las estaciones, los sensores y los semáforos visuales **no dividen** la vía en segmentos. Son elementos operativos que residen "dentro" del segmento.

## 3. El Manifiesto de los 6 Mandamientos del Bloqueo

### 1. Propiedad Exclusiva (Atomicidad)
Por defecto, un segmento solo puede tener un dueño (`Train`). Cuando un tren adquiere un segmento, lo hace en ambos sentidos de la marcha de forma atómica. Ningún otro tren puede entrar en ese tramo de vía, ni por delante ni por detrás.

### 2. Modelo del Gusano (Ocupación Dinámica)
Un tren posee una **lista dinámica de segmentos ocupados**. 
- **Crecimiento**: Cuando la cabeza física del tren (locomotora) entra en un nuevo segmento, este se añade a su lista de propiedad.
- **Encogimiento**: Un segmento anterior solo se elimina de la lista y se libera cuando la cola del tren (último vagón) lo ha abandonado totalmente y ha despejado el nodo de decisión.

### 3. Frontera Infranqueable (El Nodo como Aduana)
Los Nodos (Forks) actúan como aduanas infranqueables. Un tren **tiene prohibido** entrar físicamente en un Fork si no posee el segmento de origen y ha bloqueado con éxito el segmento de destino.

### 4. Liberación Retardada (Protección de Desvíos)
El segmento anterior NO se libera cuando el último vagón sale de sus raíles físicos, sino cuando el último vagón ha cruzado completamente el Fork y está ya en el nuevo segmento.

### 5. Comprobación Inmediata y Frenado Proactivo
En cuanto la cabeza del tren entra en un Segmento A, intenta bloquear inmediatamente el Segmento B (el siguiente en su ruta). Si el bloqueo de B falla, el tren inicia el **frenado de emergencia** en ese mismo instante.

### 6. Alineamiento y Bloqueo de Agujas (Fork Locking)
El bloqueo de un segmento a través de un Fork implica una acción física automática:
- **Modo Automático**: El Fork se orienta solo hacia el segmento bloqueado por el `AutoPilot`.
- **Anclaje Lógico**: Mientras un segmento esté bloqueado, el Fork correspondiente queda "anclado" y nadie puede cambiar su posición lógicamente.
- **Bloqueo Físico Absurdo (Anti-descarrilamiento)**: Independientemente del estado de los bloqueos o del modo Shunting, un Fork tiene terminantemente prohibido moverse si hay **cualquier vehículo detectado físicamente** sobre sus raíles. La presencia física manda sobre la intención lógica.

## 4. Modo de Maniobras (Shunting Mode): La Excepción de Convivencia

El `Shunting Mode` es un estado especial y persistente del tren que permite relajar las reglas de exclusividad para permitir operaciones logísticas.

### 4.1 Naturaleza y Propagación
- **Activación Manual**: Solo se puede activar con el tren totalmente detenido.
- **Limitación de Velocidad**: El tren entra en un estado de limitación física (Vel 1-2).
- **Propagación (La Burbuja de Maniobra)**:
    - **Requisito de Entrada**: Un tren **solo** puede entrar en un segmento ocupado si ya tiene el `Shunting Mode` activado. 
    - **Regla de Parada Total**: Para que un tren en Shunting pueda invadir un segmento ocupado, el tren que ya está dentro debe estar en **Parada Total (Velocidad = 0)**. No se permiten aproximaciones a trenes en movimiento.
    - **Control de Agujas**: Durante el estado de "Zona de Maniobra" (propiedad compartida), el anclaje lógico se relaja, permitiendo operar los Forks libremente según las necesidades de maniobra, siempre respetando el **Bloqueo Físico** del Mandamiento 6.

### 4.2 Propiedad Compartida de Segmentos
En este modo, el sistema permite que varios trenes sean dueños del mismo segmento simultáneamente. El segmento registrará a todos como propietarios concurrentes y ningún tercer tren ajeno a la maniobra podrá entrar.

### 4.3 La Condición de Salida y Casos de División
- **Regla de Soledad**: Solo se puede desactivar el modo Shunting si el tren es el único dueño lógico de sus segmentos.
- **Unlink Automático**: Si un tren en modo Normal realiza una operación de desenganche (`Unlink`) que resulte en dos entes separados compartiendo el mismo segmento, **ambos trenes pasarán automáticamente a modo Shunting**. Esto garantiza que la nueva situación de "convivencia" esté protegida por las reglas de baja velocidad.

## 5. Ciclo de Vida: El Protocolo de "Tabula Rasa"

Para garantizar la robustez máxima ante cambios en el mapa realizados por el usuario, el sistema de seguridad se reconstruye íntegramente:

1. **Detección de Cambios**: Al salir del modo edición ("Rails"), se inicia una pausa lógica atómica.
2. **Tabula Rasa**: Se destruye el Grafo anterior y se limpia por completo el `BlockManager`.
3. **Redescubrimiento**: El `TopologyService` genera un nuevo Grafo.
4. **Re-Bloqueo y Desahucio**:
   - Cada tren reclama su posición física. Si hay conflicto, pasan a modo `Shunting`.
   - **Integridad de Edición**: El sistema impide modificar o eliminar cualquier raíl que tenga un vehículo encima.
   - **Protocolo de Muerte (Desahucio)**: Si un tren pierde su base física por error, arderá y desaparecerá. Este proceso incluye la **liberación obligatoria y total** de todos sus bloqueos en el `BlockManager` para no dejar recursos huérfanos.

---
*Última actualización: 2026-04-28*
