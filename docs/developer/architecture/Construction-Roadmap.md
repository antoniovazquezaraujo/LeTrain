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
- volver a abrirlo, cambiar política y recalcular;
- eliminar una entrada;
- ver coste y tiempo estimados;
- ver el total de la lista;
- decidir más tarde cuál se construye.

La interfaz de esta lista necesita una decisión propia: dónde se abre, cómo se navega por las
entradas y cómo se editan. Una opción natural es un diálogo modal similar al editor de programas,
con una tabla de trayectos y acciones de editar, recalcular, eliminar y ejecutar. No se ha cerrado
el diseño, pero conviene resolverlo en esta fase porque la lista será la puerta de entrada a todo
lo demás.

No incluye constructoras ni zonas de exclusión. Es la fase que convierte la planificación en una
actividad separada de la ejecución.

Depende de: ADR-037 y ADR-038 (parte de lista/presupuesto).

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

La idea económica es que una obra tenga que repartir su precio entre **calidad** y **rapidez**. Una
construcción de calidad tarda más; una construcción rápida tenderá a tener peor calidad. Conseguir
las dos cosas a la vez debe ser muy caro. Como en esta fase todavía no existirá la calidad real, el
primer modelo solo podrá manejar el tiempo: a menor plazo, mayor precio de constructora. La
calidad se mostrará como promesa comercial, pero no tendrá efecto hasta la fase de mantenimiento.

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
