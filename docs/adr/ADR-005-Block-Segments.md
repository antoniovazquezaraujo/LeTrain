[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-005: Sistema de Seguridad y Ciclo de Vida de la Red

## 1. Contexto y Objetivos
Este documento define la arquitectura de seguridad de LeTrain. Tras varios intentos descartados que resultaron en bloqueos parciales e inconsistencias, se establece un modelo de **Segmentos Atómicos** y una gestión de estado **efímera**. El objetivo es garantizar que nunca haya dos trenes en el mismo espacio físico (salvo en maniobras controladas) y que los desvíos (Forks) estén siempre protegidos.

## 2. Definición de la Unidad Fundamental: El Segmento
Un **Segmento** es el tramo indivisible de vía que el sistema de seguridad protege como un todo. Es la unidad mínima de propiedad.
- **Fronteras Reales**: Un segmento nace y muere exclusivamente en puntos de decisión lógica: **Forks** (desvíos/agujas) o **Fines de vía** (topes).
- **Entidades Contenidas**: Las estaciones, los sensores y los semáforos visuales **no dividen** la vía en segmentos. Son elementos operativos que residen "dentro" del segmento.
- **Consecuencia de Diseño**: Si un tren está en una estación a mitad de un segmento, todo el tramo desde el Fork anterior hasta el Fork siguiente queda bloqueado. Esto obliga al jugador a diseñar apartaderos (usando Forks) si desea que los trenes se crucen o se adelanten.

## 3. El Manifiesto de los 6 Mandamientos del Bloqueo

### 1. Propiedad Exclusiva (Atomicidad)
Por defecto, un segmento solo puede tener un dueño (`Train`). Cuando un tren adquiere un segmento, lo hace en ambos sentidos de la marcha de forma atómica. Ningún otro tren puede entrar en ese tramo de vía, ni por delante ni por detrás, eliminando de raíz las colisiones frontales y los alcances.

### 2. Modelo del Gusano (Ocupación Dinámica)
Un tren posee una **lista dinámica de segmentos ocupados**. 
- **Crecimiento**: Cuando la cabeza física del tren (locomotora) entra en un nuevo segmento, este se añade a su lista de propiedad.
- **Encogimiento**: Un segmento anterior solo se elimina de la lista y se libera cuando la cola del tren (último vagón) lo ha abandonado totalmente y ha despejado el nodo de decisión.
- **Resultado**: Un tren puede poseer simultáneamente múltiples segmentos si su longitud física los abarca todos.

### 3. Frontera Infranqueable (El Nodo como Aduana)
Los Nodos (Forks) actúan como aduanas infranqueables. Un tren **tiene prohibido** entrar físicamente en un Fork si no se cumplen dos condiciones simultáneas:
1. Ya posee el segmento por el que llega al Fork (Segmento de Origen).
2. Ha conseguido bloquear con éxito el segmento al que pretende salir (Segmento de Destino).
Esto garantiza que ningún tren se quede detenido "encima" de un Fork bloqueando el paso a otros tramos.

### 4. Liberación Retardada (Protección de Desvíos)
Para evitar que un tren "suelte" la vía demasiado pronto y permita que otro tren mueva las agujas de un Fork mientras él todavía está pasando, se aplica una liberación retardada:
- El segmento anterior NO se libera cuando el último vagón sale de sus raíles físicos.
- Se libera solo cuando el último vagón ha cruzado completamente el Fork y está ya en el primer raíl del nuevo segmento. El Fork está protegido por la posesión de ambos segmentos durante el tránsito.

### 5. Comprobación Inmediata y Frenado Proactivo
El sistema de seguridad no espera a estar frente al Fork para pedir permiso. 
- En cuanto la cabeza del tren entra en un Segmento A, intenta bloquear inmediatamente el Segmento B (el siguiente en su ruta).
- Si el bloqueo de B falla, el tren inicia el **frenado de emergencia** en ese mismo instante. El objetivo es asegurar que la velocidad sea cero antes de invadir el Nodo de salida.

### 6. Alineamiento y Bloqueo de Agujas (Fork Locking)
El bloqueo de un segmento a través de un Fork implica una acción física automática:
- **Modo Automático**: El Fork se orienta solo hacia el segmento bloqueado por el `AutoPilot`.
- **Seguridad y Anclaje**: Mientras un segmento esté bloqueado (ya sea por un tren manual o automático), el Fork correspondiente queda "anclado" y nadie puede cambiar su posición. Esto evita descarrilamientos por cambios de aguja accidentales bajo las ruedas del tren.

## 4. Modo de Maniobras (Shunting Mode): La Excepción de Convivencia

El `Shunting Mode` es un estado especial y persistente del tren que permite relajar las reglas de exclusividad para permitir operaciones logísticas (enganches, desenganches y organización de vagones).

### 4.1 Naturaleza y Propagación
- **Activación Manual**: Solo se puede activar con el tren totalmente detenido.
- **Limitación de Velocidad**: El tren entra en un estado de limitación física (Vel 1-2) y activa una señalización visual de peligro.
- **Propagación (La Burbuja de Maniobra)**: El estado Shunting es una "capacidad" que el tren lleva consigo y que le permite interactuar con segmentos ocupados bajo reglas estrictas:
    - **Requisito de Entrada**: Un tren **solo** puede entrar en un segmento ocupado si ya tiene el `Shunting Mode` activado (lo que garantiza velocidad baja y precaución).
    - **Apertura Controlada**: Mientras un tren en Shunting esté dentro de un segmento, este se considera "Zona de Maniobra". Esto permite que otros trenes (siempre que también activen su modo Shunting) puedan entrar para colaborar en la maniobra.
    - **Protección de Alta Velocidad**: Un tren en modo Normal (alta velocidad) tiene terminantemente prohibido entrar en un segmento ocupado, aunque el tren que esté dentro esté en modo Shunting. La exclusividad solo se rompe entre "Shunters".

### 4.2 Propiedad Compartida de Segmentos
En este modo, el sistema permite que **varios trenes sean dueños del mismo segmento simultáneamente**. 
- Si el Tren A (modo normal) ocupa un segmento, el Tren B (modo Shunting) puede entrar en él.
- El segmento registrará a ambos como propietarios concurrentes. Mientras el segmento sea compartido, todos los trenes implicados operarán a velocidad de maniobra. Ningún tercer tren ajeno a la maniobra podrá entrar.

### 4.3 La Condición de Salida (Regla de Soledad)
El usuario decide cuándo desactivar el modo Shunting, pero el sistema solo lo permitirá si se garantiza la seguridad:
- El tren debe volver a ser el **único dueño lógico** de todos los segmentos que su cuerpo físico ocupa.
- **Resolución por Enganche**: Si los trenes se fusionan (Link), se convierten en una sola entidad. Al haber un solo dueño, el modo Shunting puede desactivarse.
- **Resolución por Alejamiento**: Si no hay enganche, el tren debe moverse físicamente hasta salir del segmento compartido. Solo cuando su cola despeja el Fork y entra en un segmento donde está solo, podrá desactivar el modo Shunting.

## 5. Ciclo de Vida: El Protocolo de "Tabula Rasa"

Para garantizar la robustez máxima ante cambios en el mapa realizados por el usuario, el sistema de seguridad no intenta mantener la identidad persistente de los segmentos, sino que se reconstruye íntegramente:

1. **Detección de Cambios**: Al salir del modo edición ("Rails"), si el usuario ha realizado cambios en la red, se inicia una pausa lógica atómica en el simulador.
2. **Tabula Rasa (Borrado Total)**: Se destruye el Grafo ferroviario anterior y se limpia por completo el estado del gestor de bloqueos, borrando todas las ocupaciones actuales.
3. **Redescubrimiento**: El `TopologyService` realiza un escaneo de la nueva red física y genera un nuevo Grafo con identificadores de segmento efímeros (ej. S1, S2, S3).
4. **Re-Bloqueo por Posición Física**: Cada tren superviviente en el mapa consulta su ubicación física actual (`RailTrack`) en el nuevo Grafo y solicita de nuevo el bloqueo automático del segmento que está pisando.
   - **Conflicto Post-Edición**: Si tras la edición dos trenes terminan en el mismo segmento (ej. el usuario ha borrado el Fork que los separaba), el sistema les asigna automáticamente el modo `Shunting` por seguridad hasta que se resuelva el conflicto.
   - **Desahucio**: Si un tren pierde su base física (vía borrada), se marca como descarrilado y se anulan sus bloqueos.

---
*Última actualización: 2026-04-28*
