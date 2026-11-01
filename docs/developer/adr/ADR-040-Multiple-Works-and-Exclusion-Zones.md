# ADR-040: Varias obras, zonas de exclusión y edición manual fuera

## Estado: PROPUESTO (fase 3, sin implementar)

> Esta ADR prepara la fase 3 de [[Construction-Roadmap|Construction-Roadmap]]. Las fases 1 y 2
> están implementadas en 2D: lista de borradores, cotizaciones, `Run`, prepago, cancelación,
> gasto real y persistencia en savegames. La cola sigue siendo de **una sola obra activa** y el
> bloqueo de edición es global.
>
> Depende de [[ADR-037-Construction-Queue|ADR-037]] y
> [[ADR-038-Planned-Works-and-Contractors|ADR-038]]. La calidad real y el mantenimiento quedan en
> [[ADR-039-Track-Quality-and-Maintenance|ADR-039]].

## Contexto

El flujo de ejecución actual es deliberadamente pequeño:

- una única `RouteConstructionQueue` en el presenter;
- un único `AutoConstructionBudget` con su cotización y su reserva;
- `isConstructionActive()` bloquea la edición manual en todo el mapa;
- guardar una partida se bloquea mientras la obra está activa porque la cola no se serializa;
- al terminar o cancelar, la obra libera ese único hueco y la lista conserva `Done` o `Cancelled`.

La fase 3 quiere que el jugador pueda tener **varias obras en marcha sin que se pisen**, seguir
construyendo a mano en otras zonas y cancelar cada obra por separado. Eso exige separar la cola de
la UI, reservar territorio y hacer el bloqueo de edición selectivo.

## Decisiones propuestas

### 1. La unidad persistente es el "tajo" (construction site)

Cada obra que pasa de `Run` a ejecución crea un **tajo** con, como mínimo:

- id de la obra y su `RoutePlan`/cotización;
- estado de construcción y progreso (celdas construidas);
- presupuesto prepagado y gasto acumulado;
- zona de exclusión reservada;
- identificador de color.

La lista `PlannedWork` sigue siendo la vista de negocio del plan; el tajo es el estado de ejecución.
No se guarda un `RouteConstructionQueue` de UI en el modelo: se guarda el estado necesario para
reconstruir la cola al cargar.

### 2. Zona de exclusión

Se mantiene la decisión de [[ADR-038-Planned-Works-and-Contractors|ADR-038]]: **un rectángulo
simple por tajo**, sin varios rectángulos por ruta.

Propuesta concreta:

- La zona se calcula con el rectángulo que contiene todos los `RouteStep` del plan.
- Se amplía con un margen de seguridad de **2 celdas** por cada lado (cifra revisable).
- Se reserva al arrancar el tajo, no al guardar el borrador.
- Dos tajos no pueden tener zonas que se intersequen, ni siquiera tocándose.
- La zona se libera al pasar a `Done` o `Cancelled`.
- El color se deriva del id de la obra y se usa en el mapa y en la lista.

Los borradores **no** reservan zona: se pueden solapar mientras no se ejecuten. La comprobación de
solape se hace al arrancar y se repite antes de cada intento de `Run`.

### 3. Varios tajos activos

El límite inicial sigue siendo **cuatro equipos** (ADR-038). Se puede arrancar un tajo si:

- la obra es `DRAFT`;
- tiene cotización viable;
- hay saldo suficiente para su presupuesto;
- hay un equipo libre;
- su zona no se solapa con ningún tajo activo;
- el preflight de ocupación de la vía sigue pasando.

La cola deja de ser una sola. El modelo mantendrá una colección de tajos activos y cada uno
avanzará con su propio presupuesto y progreso. El presenter solo reflejará el estado y enviará
acciones.

Queda abierto si los tajos se arrancan todos a la vez o si el jugador puede ordenarlos. La
primera rebanada puede arrancar en orden de selección y mantener el límite de cuatro.

### 4. Edición manual fuera de las zonas

El bloqueo global `isConstructionActive()` desaparece como criterio único. Se sustituye por una
política de edición basada en zonas:

- se puede construir, borrar y editar fuera de todas las zonas activas;
- se rechaza cualquier edición cuya celda destino caiga dentro de una zona;
- el rechazo debe ser visible y explicar qué obra reserva la zona;
- la navegación, la cámara y `Drive` siguen disponibles;
- las acciones que afectan a varias celdas se validan celda a celda antes de aplicar nada.

La política debe ser una pieza de core/UI-free, no un `if` disperso en cada modo. Los comandos de
consola y el editor de programas usarán la misma comprobación que las teclas.

### 5. Dinero y cancelación

Cada tajo conserva su propio prepago y su propio reembolso:

- al arrancar se cobra la cotización completa;
- la reserva queda asociada al tajo, no a la lista global;
- al cancelar se devuelve lo no construido menos la penalización;
- el gasto real se muestra en la fila `Cancelled`, como ya ocurre hoy;
- el saldo nunca puede quedar descubierto por una obra automática.

La penalización por empresa y el posible recargo por simultaneidad quedan como decisiones abiertas
de balance. La fase 3 no necesita cambiar la fórmula provisional para funcionar, pero sí debe
aislar el cálculo por tajo.

### 6. Persistencia y save/load

Hoy guardar se bloquea si hay una obra automática activa. Con varios tajos eso sería inaceptable,
así que la fase 3 debe persistir el estado de ejecución:

- lista de tajos con plan, cotización, progreso, presupuesto y zona;
- estado de cada tajo (`WORKING`, `DONE`, `CANCELLED`);
- ningún objeto de UI, listener o materializador;
- al cargar, reconstruir las colas y continuar desde el progreso guardado.

Si una rebanada intermedia no puede persistir todavía la cola, debe mantener el bloqueo de guardado
de forma explícita y temporal; no debe guardar un `WORKING` huérfano.

### 7. Interfaz 2D

La lista de Planning añadirá:

- indicador de tajos activos/equipos libres;
- progreso por fila de las obras `WORKING`;
- acción de cancelar por tajo, ya existente;
- color de zona coherente con el mapa;
- total de reservas de los tajos activos, separado del total de borradores.

El mapa pintará el rectángulo de cada zona y su color. La construcción manual fuera de las zonas
seguirá usando los overlays actuales.

### 8. 3D

La ventana de Planning 3D sigue fuera de esta ADR. La entrada del menú 3D permanece oculta hasta
que exista una UI propia; las zonas y la cola múltiple se diseñan primero para 2D.

## Fuera de alcance

- Calidad real, desgaste, mantenimiento y averías (ADR-039).
- Comando `connect` y automatización por DSL.
- Varios rectángulos por ruta o zonas no rectangulares.
- Edición manual dentro de una zona reservada.
- Planning 3D.
- Cambios de balance definitivos para el recargo por simultaneidad.

## Consecuencias

- La cola deja de ser un objeto único del presenter y pasa a ser estado de modelo serializable.
- El bloqueo global de edición se sustituye por una política de zonas reutilizable.
- El savegame gana una sección de ejecución además de la lista de planificación.
- La lista conserva su papel de planificación; el tajo es la ejecución.
- El jugador puede seguir trabajando en el mapa mientras varias obras avanzan.

## Criterios de aceptación

1. Dos obras con zonas disjuntas pueden estar `WORKING` a la vez y ambas terminan.
2. Una obra cuya zona solapa con otra activa es rechazada antes de cobrar o arrancar.
3. Se puede construir manualmente fuera de las zonas y se rechaza dentro con un aviso claro.
4. Cancelar una obra libera solo su zona y su equipo; las demás siguen.
5. El límite de cuatro equipos se respeta y se explica en la UI.
6. Guardar y cargar con tajos activos reconstruye progreso, reserva y zonas sin duplicar cobros.
7. Los borradores siguen pudiendo solaparse; solo las ejecuciones reservan zona.
8. Una obra terminada o cancelada no bloquea edición ni guardado.

## Rebanadas de implementación

1. **3a: zonas y política de edición** con una sola obra activa. Validar solapes, pintar la zona y
   permitir edición fuera. Sin cola múltiple todavía.
2. **3b: varios tajos** y equipos. Sustituir la cola única por una colección, mantener límite de
   cuatro y conservar las acciones actuales.
3. **3c: persistencia de tajos activos** y eliminación del bloqueo de guardado.
4. **3d: pulido de UI** (progreso, colores, totales, avisos y pruebas de integración).

## Decisiones abiertas

- Margen exacto de la zona (propuesta: 2 celdas).
- Si los tajos se arrancan simultáneamente o en orden elegido.
- Reparto exacto del recargo por simultaneidad y política de cancelación por empresa.
- Qué ocurre si una zona nueva intenta reservar celdas ya construidas manualmente.
- Persistencia de la cola en la primera rebanada 3a o solo a partir de 3c.
