# Roadmap de construcción y mantenimiento

Este documento ordena las ideas acordadas para no mezclar lo que ya existe con lo que todavía es
una visión de futuro. No sustituye a las ADRs: sirve para decidir qué se implementa primero.

## Punto de partida

La rama `feature/construction-queue` ya tiene una primera cola funcional:

- se elige una ruta en la preview y se construye inmediatamente;
- una sola obra activa;
- 1 tick base por celda más los delays reales de puente/túnel;
- coste real, sin recargo automático;
- ruta aislada, sin autoconxión ni forks;
- preflight de celdas ocupadas;
- trenes libres; navegación, cámara, modos y Drive disponibles;
- edición manual bloqueada globalmente;
- HUD, confirmación de cancelación y aviso de vía ocupada en la preview.

Eso está documentado en [[ADR-037-Construction-Queue|ADR-037]] y es lo que se puede probar hoy.
No hay que rehacerlo para empezar las fases siguientes.

## Fase 1: lista de planes y presupuestos

Objetivo: poder preparar varias rutas sin construir ninguna.

Incluye:

- guardar un plan en una lista;
- volver a abrirlo;
- cambiar su política y actualizar el presupuesto;
- eliminar una entrada;
- ver coste y tiempo estimados;
- ver el total de la lista;
- decidir más tarde cuál se construye.

La interfaz de esta lista necesita una decisión propia: dónde se abre, cómo se navega por las
entradas y cómo se editan. Se abre como un diálogo modal parecido al editor de programas, con una
tabla de trayectos, acciones por atajos y una línea de total. La lista será la puerta de entrada a
todo lo demás.

No incluye constructoras ni zonas de exclusión. Es la fase que convierte la planificación en una
actividad separada de la ejecución.

Depende de: ADR-037 y ADR-038 (parte de lista/presupuesto). La lista se abre desde el mapa con
`b` (Works).

### Lista de la fase 1

La primera versión no debe mostrar todavía constructoras ni calidades: esa información todavía no
existe. La tabla debe enseñar lo que el planner ya sabe calcular y lo que el usuario necesita para
decidir qué conservar.

```text
Obras
 #  Estado    Desde   Hasta   Política    Celdas  Coste       Tiempo
 1  Borrador  20,25   34,254  CHEAPEST    234     $342.000    12 min
 2  Borrador  53,85   92,329  AVOID_ALL   134     $34.000     3 min
 3  Borrador  12,10   44,80   DIRECT      98      $9.800      2 min
────────────────────────────────────────────────────────────────────────
Total: 3 obras · $385.800 · 17 min

[A] Add   [E] Edit   [D] Delete   [Esc] Close
[Run] disabled until construction companies and cash are available
```

La tabla usa **celdas**, no kilómetros, porque el juego no tiene todavía una escala de distancia
definida para la autoría. El coste es el coste real del `RoutePlan` y el tiempo es el tiempo de
construcción estimado que ya muestra la preview (ticks del planner más la base por celda). No hay
recargo de constructora en esta fase.

La columna `Política` es imprescindible: dos planes con los mismos extremos pueden tener rutas y
costes distintos según sean `CHEAPEST`, `DIRECT`, `AVOID_WATER`, etc. Sin ella, el usuario no sabe
qué está guardando. La columna `Estado` también es necesaria desde el principio, aunque en fase 1
solo tenga el valor `Borrador`; los estados de ejecución llegarán con las constructoras.

La acción `Run` debe aparecer deshabilitada en esta fase. Primero se preparan y comparan rutas;
contratarlas y ejecutarlas pertenece a la fase 2. `E` edita la ruta seleccionada y `D` elimina la
entrada. `Esc` cierra la lista. La fila seleccionada se conserva al reabrir el diálogo.

`Edit` abre la ruta guardada directamente en la preview, con A/B ya fijados y el plan actual
visible. No vuelve a pedir los extremos y no permite cambiarlos: si el usuario quiere otro origen o
destino, debe crear otra obra. Solo puede cambiar la política con `[`/`]`; al aceptar, se actualiza
la fila con el nuevo plan, coste, celdas y tiempo, y las marcas de los extremos no cambian. Mientras
se edita, la ruta antigua se oculta del mapa: solo se pinta el plan candidato, para que dos políticas
no se superpongan.

El detalle de una obra y el recálculo suelto quedan fuera de esta primera rebanada. El detalle
necesita una pantalla propia; el recálculo suelto no aportaba nada porque editar la política ya
recalcula cuando hace falta.

La acción `Add` no devuelve al mapa normal ni crea una obra inmediatamente. Al pulsarla:

1. El diálogo de la lista se cierra.
2. El mapa entra en el flujo de planificación de ruta, con su propia barra de estado:
   `Add route · PICK_START · move + Enter · Esc cancel`.
3. El usuario elige A y B, puede cambiar de política y ve la ruta como en la preview normal.
4. Al aceptar, la ruta se guarda en la lista y se vuelve al mapa normal. No se construye nada.
5. Si cancela, se vuelve al mapa normal sin añadir nada.

Durante ese flujo de `Add`, el menú normal de modos queda oculto o deshabilitado: el usuario está
en una tarea modal de planificación y debe ver las instrucciones de A/B, no la lista de modos.
La barra de estado de `Add` ocupa una sola línea inferior, como la consola, y `Tab` la oculta o la
vuelve a mostrar. Al terminar o cancelar, vuelve el menú normal.

Las rutas guardadas se pintan en el mapa en todo momento, no solo mientras la lista está abierta:
permanecen visibles hasta que se elimina su entrada (o, en una fase posterior, se construyen).

Esto es importante: el `Add` usa el modo de planificación en **modo guardar**, no el flujo actual
que selecciona y arranca la cola. La ruta queda en la lista como `Borrador`; para construirla hay
que volver a la lista y usar `Run` cuando esté disponible en la fase 2.

El diálogo de la lista no se reabre solo después de añadir, cancelar o terminar una obra. Cuando
el usuario acepta o cancela una planificación, se queda en el mapa con su menú normal. Si quiere
volver a la lista, la abre de nuevo él mismo. El estado de la lista —filas, orden, selección y
total— se conserva en el presenter, así que al reabrirla aparece actualizada, pero nunca roba el
foco ni interrumpe lo que el usuario esté mirando. Los avisos de “añadida”, “terminada” o
“cancelada” se muestran en la barra de estado del mapa, no abriendo el diálogo por sorpresa.

La lista se abrirá como un diálogo modal parecido al editor de programas: filas seleccionables con
las flechas, acciones por atajos y una línea de total. En una terminal de 80 columnas la tabla
completa puede no caber; la solución es mantener las columnas esenciales y mostrar los avisos en la
propia fila.

Al guardar una ruta se crean dos marcas con los extremos: `Way-<id>-start` y `Way-<id>-end`, donde
`<id>` es el id de la obra en la lista (por ejemplo, `Way-1-start` y `Way-24-end`). Las marcas
permanecen al eliminar la fila; su gestión tendrá su propia opción de menú más adelante.

Implementación actual de la fase 1:

- `core/.../construction/PlannedWork`: entrada inmutable con id, extremos, política, `RoutePlan`,
  coste, tiempo estimado y estado `DRAFT`.
- `core/.../construction/ConstructionPlanList`: orden de inserción, ids estables, eliminación y
  totales.
- `core/.../planner/RoutePreviewSession.beginEdit`: abre un plan guardado en PREVIEW sin volver a
  pedir A/B y permite cambiar la política.
- `ui-terminal/.../ConstructionPlansDialog`: diálogo modal con tabla, total y acciones; `Run`
  deshabilitado.
- `TerminalPresenter`: flujo `Add` en modo guardar, flujo `Edit` sobre el plan guardado, apertura con
  `b`, borrado y creación de las marcas de extremos. El diálogo no se reabre solo.

La lista es presenter-owned y se descarta al cambiar de modelo; todavía no se guarda en savegames.

### Visión final de la lista

Cuando existan constructoras, la tabla crecerá con la oferta seleccionada. La maqueta objetivo es:

```text
Obras
 #  Estado    Desde   Hasta   Política    Celdas  Constructora  Calidad   Plazo  Coste
 1  Borrador  20,25   34,254  CHEAPEST    234     CONSTRASA     Standard  Slow   $342.000
 2  Borrador  53,85   92,329  AVOID_ALL   134     CHAPUCERT     Low       Fast   $34.000
────────────────────────────────────────────────────────────────────────────────────
Total: 2 obras · $376.000
[A] Add  [E] Edit  [Q] Quotes  [D] Delete  [R] Recalculate  [Run] Run
```

La columna `Constructora` no debe ocultar que una ruta puede tener varias cotizaciones. La acción
`Q` abrirá la comparación de ofertas de la fila seleccionada. `Run` seguirá comprobando efectivo y
reserva antes de arrancar.


## Fase 2: constructoras, cotizaciones y economía automática

Objetivo: que cada plan tenga ofertas distintas y que subcontratar cueste dinero y tiempo.

Incluye:

- perfiles de constructoras con precio, plazo y capacidades;
- cotizaciones por plan;
- recargo de automatización;
- dinero necesario por adelantado;
- reserva del presupuesto;
- penalización de cancelación;
- comparación de ofertas en la lista.

La idea económica es que una obra tenga que repartir su precio entre **calidad** y **rapidez**. Una construcción de calidad tarda más; una construcción rápida tenderá a tener peor calidad. Conseguir las dos cosas a la vez debe ser muy caro. Como en esta fase todavía no existirá la calidad real, el primer modelo solo podrá manejar el tiempo: a menor plazo, mayor precio de constructora. La calidad se mostrará como promesa comercial, pero no tendrá efecto hasta la fase de mantenimiento.

No incluye calidad real ni mantenimiento.

Depende de: Fase 1 y ADR-038.

## Fase 3: varias obras, zonas de exclusión y edición manual fuera

Objetivo: ejecutar varias obras sin que se pisen y dejar que el jugador siga trabajando en otras
zonas.

Incluye:

- varias obras activas;
- una zona rectangular simple por obra, con color identificativo;
- margen de seguridad;
- visualización de zonas en el mapa;
- rechazo de obras que se solapen;
- edición manual permitida fuera de las zonas;
- liberación de zonas al terminar o cancelar;
- progreso y cancelación individual.

El punto delicado es la intersección entre planes, no el rendimiento: comprobar unas decenas de
zonas es barato si se hace al editar/arrancar y no en cada frame ni en cada vecino del A*.

Depende de: Fase 1 y Fase 2.

## Fase 4: mantenimiento y calidad

Objetivo: que la calidad de construcción y la constructora tengan consecuencias reales.

Incluye:

- `TrackSpec` por celda o tramo;
- calidad global de construcción (materiales, mano de obra y dirección como un paquete);
- desgaste por uso;
- coste de mantenimiento;
- averías y reparaciones;
- save/load compatible;
- vías antiguas con calidad por defecto.

Esta fase debe existir antes de implementar la calidad manual. Sin mantenimiento, la calidad sería
una etiqueta sin efecto.

Depende de: ADR-039 y de la economía de obras de la Fase 2.

## Fase 5: calidad y ritmo manual

Objetivo: que construir a mano también tenga decisiones de calidad y rapidez.

Incluye:

- un nivel de **construcción de calidad** que engloba materiales, mano de obra y dirección;
- ritmo rápido/normal/cuidadoso;
- coste y tiempo distintos;
- UI para elegir y consultar.

No habrá una elección separada de materiales. La calidad es un paquete único; si el jugador quiere
calidad y rapidez al mismo tiempo, será muy caro. La calidad sin rapidez tarda; la rapidez sin
calidad sale más barata pero dará más mantenimiento.

Depende de: Fase 4.

## Fase 6: automatización por comando y 3D

Objetivo: exponer la misma maquinaria fuera de la UI 2D.

Incluye:

- un único comando `connect`, no dos comandos separados para planificar y construir;
- coordenadas, marcas o estaciones como origen/destino;
- parámetros de empresa, calidad y ritmo cuando el comando automatice directamente;
- planificar, comprobar efectivo y encolar en una sola operación;
- posible variante con espera para escenarios;
- HUD y zonas en 3D;
- sonido de obras y actividad visible en ambos clientes.

Cada empresa ofrecerá su propia calidad y ritmo a un precio. Algunas empresas baratas no harán
túneles ni puentes. No hay parámetro de material separado: la calidad ya lo engloba.

La lista de presupuestos no es un requisito para el comando. La lista sirve para preparar, comparar
y ordenar varias obras antes de lanzarlas; `connect` puede funcionar sin ella usando valores por
defecto o parámetros explícitos.

Depende de: cola, constructoras y economía automática.

## Decisiones cerradas y pendientes

Ya están decididas:

- el presupuesto se reserva al arrancar cada obra, y el dinero de las obras activas queda
  comprometido;
- el manual puede quedar en negativo pagando materiales;
- una zona de exclusión es un rectángulo simple por obra, con color;
- hay un límite de cuatro equipos y la simultaneidad se cobra mucho más cara;
- habrá un único comando `connect`;
- la calidad es un paquete único (materiales, mano de obra y dirección); no hay materiales
  separados.

Siguen pendientes:

- cifras de recargo automático, ticks base y cancelación;
- cómo se reparte el sobrecoste entre las obras simultáneas;
- capacidades concretas de cada constructora;
- persistencia de la lista de obras en savegames;
- fórmula de calidad y ritmo de mantenimiento;
- sintaxis final y valores por defecto de `connect`;
- posible comando `construct` futuro para ejecutar una lista de trayectos de forma simultánea,
  seleccionada u ordenada. No se implementa ahora;
- posible sistema bancario/créditos para financiar obras. No se implementa ahora;
- diseño UI de la lista de presupuestos (probablemente un diálogo tipo editor de programas).

Estas decisiones no bloquean la Fase 1. Se pueden resolver cuando se llegue a cada fase, con datos
reales de uso.
