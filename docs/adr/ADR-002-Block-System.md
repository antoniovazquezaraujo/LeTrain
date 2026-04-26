# Definiciones de Usuario (Señales y Bloqueos)

# ADR-002: Sistema de Seguridad por Enclavamiento de Rutas (OBSOLETO)

> [!CAUTION]
> **ESTE DOCUMENTO ES OBSOLETO.**
> La lógica de bloqueo por puertos descrita aquí ha sido sustituida por el sistema de **Bloqueo por Segmentos Atómicos** definido en el [[ADR-005-Block-Segments|ADR-005]].
> Se mantiene exclusivamente por motivos históricos.

## Reglas (Antiguas)
1. Un tren detenido ha de mantener bloqueados los dos puertos que unen el segmento donde está, es decir, el puerto por el que debió salir y el puerto por el que se dispone a entrar.
2. Para bloquear un puerto, le asignamos como owner el tren que lo bloquea
3. Si un puerto está bloqueado, ningún otro tren puede bloquearlo
4. Antes de avanzar, el tren **intentará** bloquear el puerto de salida del siguiente segmento, es decir, aquel por el que desea **llegar** al siguiente nodo. Si lo consigue, entonces intentará bloquear el otro puerto del mismo segmento. Si lo consigue avanzará. Si no, dejará ambos puertos como estaban y frenará.
5. Cuando un tren no consigue avanzar se detendrá 15 segundos y volverá a reintentar el bloqueo.
6. Cuando el último vehículo del tren pasa por un nodo, desbloqueará los dos puertos que unen el segmento que está abandonando.
7. Al pasar a modo manual, se eliminan los bloqueos, y el tren puede avanzar como el usuario desee
8. Cuando una rama de un fork está bloqueada, en lugar de frenar, primero hay que mirar si la otra rama está libre y **nos lleva al mismo nodo**. En ese caso, se cambia de sentido el fork y se avanza por la otra rama.

### 2. El CANTÓN (BLOCK SECTION) - Tramo de Vía
Un cantón representa el conjunto físico de railes que une exactamente DOS puertos de DOS nodos distintos. Pero interiormente se define como un par de elementos, sus extremos, que son puertos de nodos.
- **Extremos**: El cantón se define por: `(DESDE EL PUERTO P DEL NODO N HASTA EL PUERTO Q DEL NODO M`. Es decir, por qué puerto se sale de qué nodo y a cuál se llega de qué otro nodo.
- **Unicidad**: Un raíl del mapa (que no sea un nodo) puede pertenecer a varios cantones, ya que un cantón invertido no es el mismo que sin invertir.

**Fase 1: El Bloqueo Transaccional y Máquina de Estados (Regla 4 y 5)**
Modificaremos el `AutoPilot` para que funcione como una Máquina de Estados Finita (FSM) estricta, basada en una variable interna de permiso (`permisoConcedido`). La regla de oro es: **"Para poder moverse dentro del segmento actual (N), el tren necesita tener el permiso concedido para el siguiente segmento (N+1)".**

El ciclo de evaluación en cada *tick* será:
1. **Estado Inicial:** `permisoConcedido = false`. El tren está detenido o acaba de entrar en un nuevo segmento.
2. **Evaluación Continua:**
   - Si `permisoConcedido == true`: El tren tiene vía libre para circular a velocidad de crucero por el segmento actual. No hace nada más.
   - Si `permisoConcedido == false` y el temporizador está activo: El tren espera (15 segundos) a velocidad 0.
   - Si `permisoConcedido == false` y el temporizador es 0: El tren intenta la **Transacción de Bloqueo** para el siguiente segmento:
     1. Pide el puerto de Entrada del NodoDestino.
     2. Pide el puerto de Salida del NodoOrigen.
     3. **Commit:** Si consigue ambos, `permisoConcedido = true` y el tren acelera.
     4. **Rollback:** Si falla alguno, libera *inmediatamente* los puertos que haya conseguido bloquear en este intento, frena (velocidad = 0) y activa el temporizador de 15 segundos.
3. **Transición (Paso de Nodo):** En el instante en que la cabeza del tren cruza el nodo y entra en el segmento N+1, el estado se reinicia a `permisoConcedido = false`, obligando al tren a ganarse el derecho a moverse por el nuevo segmento en el siguiente *tick*.

### 4. ITINERARIO (ITINERARY) - Hoja de Ruta
Es una secuencia ordenada de **Pasos de Itinerario** (ItinerarySteps). Cada paso vincula:
- **Cantón**: El tramo físico que el tren debe recorrer, es decir, el par (puerto y nodo desde -> puerto y nodo hasta)
- **Acción en Destino**: La operación que el tren debe realizar al llegar al nodo donde termina el cantón. 
    - **Acciones Disponibles**: PASO (no detenerse), PARADA (tiempo fijo, de momento 15 segundos), CARGA (si es una estación, los sensores no cargan nada) y DESCARGA (también solo si es una estación).
- **Conducción**: El estado de los nodos se ajusta automáticamente. Al finalizar una acción vinculada a un nodo (ej. carga completa), el tren solicita automáticamente el bloqueo para el siguiente cantón del itinerario. 
- **Cálculo**: El sistema utiliza el algoritmo **A* (Camino más corto)** para encontrar la ruta óptima en número de railes.

### 5. PROTOCOLO DE RESERVA (TREN INTELIGENTE)
El tren gestiona su propia seguridad mediante una **Burbuja de Seguridad (Efecto Gusano)**:
- **Gestión de la Punta (Entrada)**: Antes de entrar en un nuevo cantón, el tren solicita el bloqueo al `BlockManager`. Al entrar, lo añade a su lista con `BLOQUEO_TOTAL`.
- **Gestión de la Cola (Salida)**: Un tren no libera un cantón hasta que su último vehículo ha cruzado completamente el nodo de salida.
- **Freno y Re-arranque**: Si el cantón siguiente está ocupado, el tren pone su velocidad a 0 automáticamente y espera la notificación de vía libre para reanudar la marcha.

### 6. CICLO DE VIDA DEL BLOQUEO (Estados del Cantón)
Un cantón puede estar en uno de estos cuatro estados:
- **LIBRE (FREE)**: Sin reservas ni trenes.
- **BLOQUEO_TOTAL (TOTAL_BLOCK)**: El tren bloquea el cantón en el que está y también el siguiente. En ambos sentidos, es decir, bloquea ambos puertos de ambos nodos. Nadie más puede entrar por ningún extremo en el cantón.
- **BLOQUEO_DIRECCIONAL (DIRECTIONAL_BLOCK)**: Protección de cola. El tren ya ha salido físicamente del cantón. El bloqueo que tenía se retira y se sustituye por un bloqueo direccional, que implica prohibir solamente que ningún tren pueda salir por el puerto indicado en el itinerario del tren que lo mantenía bloqueado, es decir, evitamos la persecución y el alcance. 

### 7. VISUALIZACIÓN Y SEMÁFOROS
- **Semáforos**: Son indicadores visuales de la reserva del cantón siguiente. (VERDE = Permiso concedido, ROJO = Denegado/Ocupado).
- **Colores de vía**: Los railes de un cantón bloqueado se pintan del color único de la locomotora propietaria. Los nodos se mantienen en su color original para marcar visualmente las fronteras.

### 8. EDITOR DE ITINERARIOS
- Un itinerario es una lista ordenada de estaciones, cada una con una acción a realizar. 
- Cada estación, si no tiene nombre aparecerá como "Estación n" siendo n su id
- Cada itinerario, si no tiene nombre se llamará "a -> b", con el nombre de la primera estación como a y el de la última como b. **El nombre se regenera dinámicamente** — solo se usa como fallback cuando no se ha dado un nombre explícito.
- Se mostrará una lista de itinerarios con dos campos: el nombre del itinerario y el id del posible tren al que se asigna y un id del tren al que se ha asignado
- Mostrará la lista de itinerarios, cada uno con su nombre y el id del tren al que se ha asignado
- Permitirá agregar, eliminar o editar cada itinerario
- Permitirá asignarle o quitarle un tren, a elegir entre los que hay
- Si una estación no se puede alcanzar, siguiendo el itinerario desde la primera estación, no se podrá agregar
- **Un tren solo puede tener asignado UN itinerario a la vez** (relación 1:1)
- **Un itinerario no se puede guardar hasta que tenga al menos 2 estaciones**
- **Cada vez que se cambia algo del mapa, hay que llamar a `model.getBlockManager().discoverBlocks()`** para que se rediscoveran los cantones y se revaliden las rutas
 

### 9. ASPECTO DEL EDITOR DE ITINERARIOS

#### Ventana Principal: Lista de Itinerarios
**Propósito**: Listar itinerarios y gestionar asignaciones de tren.

```
┌─────────────────────────────────────────┐
│            Itinerarios                  │
├─────────────────────────────────────────┤
│ Ruta                │ Tren              │
├─────────────────────────────────────────┤
│ Paris → Barcelona   │ [Train 2    ▼]    │
│ Vigo → Ourense      │ [           ▼]    │
│ Madrid → Toledo     │ [Train 1    ▼]    │
├─────────────────────────────────────────┤
│[Agregar]    [Eliminar]                  │
│                                         │
│                     [Aceptar][Cancelar] │
└─────────────────────────────────────────┘

```

**Detalles**:
- **Encabezados**: Dos columnas con "Ruta" y "Tren"
- **Dropdown de tren**: Muestra trenes disponibles + opción vacía para desasignar
- **`[ ]` (vacío)**: Indica itinerario sin tren asignado
- **Botón `[Agregar]`**: Crea nuevo itinerario (abre ventana de detalle)
- **Botón `[Eliminar]`**: Elimina el itinerario seleccionado
- **Doble clic o Enter** en fila: Abre ventana de detalle para editar estaciones
- **Tecla Suprimir**: Elimina el itinerario seleccionado
- Validación de relaciones 1:1: solo se permiten asignar trenes que no tengan ya un itinerario asignado

#### Ventana de Detalle: Editor de Estaciones
**Propósito**: Editar las estaciones y sus acciones. Los trenes se gestionan en la ventana principal.

```
┌─────────────────────────────────────────┐
│ Itinerario [Madrid → Salamanca ]        │
├─────────────────────────────────────────┤
│ Estaciones:                             │
│ ┌─────────────────────────────────────┐ │
│ │ Madrid         │ [PASO      ▼]      │ │
│ │ Ciudad Real    │ [PARADA    ▼]      │ │
│ │ Zamora         │ [CARGA     ▼]      │ │
│ │ Salamanca      │ [DESCARGA  ▼]      │ │
│ └─────────────────────────────────────┘ │
│ [Agregar]  [Eliminar]                   │
│                      [Aceptar][Cancelar]│
└─────────────────────────────────────────┘
```

**Detalles**:
- **Campo nombre**: Editable, con valor por defecto generado automáticamente ("origen → destino")
- **Lista de estaciones**: 
  - Columna izquierda: Nombre de la estación (no editable). Si no tiene nombre, se muestra "Estación n"
  - Columna derecha: Dropdown con acciones disponibles [PASO | PARADA | CARGA | DESCARGA]
- **Botón `[Agregar]`**: Abre selector de estaciones alcanzables desde la última estación de la lista. Solo se pueden agregar estaciones que tengan ruta desde la última estación.
- **Botón `[Eliminar]`**: Elimina la estación seleccionada
- **Tecla Suprimir**: También elimina la estación seleccionada
- **Validación**: El itinerario debe tener al menos 2 estaciones para poder guardar
- **NO hay campo de tren**: La asignación se hace en la ventana principal

#### Reglas de Validación
- **Nombre dinámico**: Si no se especifica un nombre, se genera como "origen → destino". El nombre se regenera automáticamente si cambian las estaciones.
- **Estaciones alcanzables**: Solo se pueden agregar estaciones que tengan ruta desde la última estación del itinerario
- **Mínimo de estaciones**: Un itinerario no se puede guardar con menos de 2 estaciones
- **Relación 1:1 tren-itinerario**: Un tren solo puede tener un itinerario asignado, y un itinerario solo puede tener un tren asignado
- **Validación de infraestructura**: Cada vez que se modifica el mapa, debe llamarse a `model.getBlockManager().discoverBlocks()` para redescubrir cantones y revalidar rutas.

## Posibles inconvenientes##
   1. El "Tick" Fantasma: ¿Qué pasa si el tren ya estaba ocupando el puerto de Salida del NodoOrigen desde el tick anterior? Al hacer el "Rollback", no deberíamos liberar ese puerto, porque el tren sigue estando ahí físicamente. El Rollback solo debe deshacer las reservas nuevas de ese tick.

   En efecto, el proceso sería:
    ```
        viaLibre = false
        if (puedo bloquear el puerto de llegada){
            if (puedo bloquear el de salida){
                víaLibre = true
            }else{
                desbloqueo el de llegada
            }
        }
    ```

   2. Los Forks (Desvíos): Un Fork tiene 3 puertos. Reservar "Entrada Destino" es ambiguo en un Fork si no sabemos exactamente hacia qué rama se va a orientar. ¿Debería la transacción incluir la orientación del Fork de forma atómica?

   Pero lo sabemos porque eso nos lo da nuestro itinerario, no?

   3. El alcance de la visión (Look-ahead): ¿Reservar solo el siguiente segmento es suficiente para evitar bloqueos mutuos en vías únicas largas (sin apartaderos)? Si dos trenes entran en extremos opuestos de un túnel largo (varios segmentos), la Fase 1 no evitaría el choque, solo haría que frenen uno frente al otro en medio del túnel.

   En efecto. No es buena idea dividir un tunel en varios segmentos si no queremos que pase eso.


## Cosas que no entiendo aún ##
En qué momento se realiza la acción de reserva de bloques? 


---
*Última actualización: 2026-04-21*
