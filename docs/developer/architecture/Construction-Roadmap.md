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

No incluye calidad real ni mantenimiento: la calidad de la constructora se mostrará como promesa,
pero su efecto llega en la fase de mantenimiento.

Depende de: Fase 1 y ADR-038.

## Fase 3: varias obras, zonas de exclusión y edición manual fuera

Objetivo: ejecutar varias obras sin que se pisen y dejar que el jugador siga trabajando en otras
zonas.

Incluye:

- varias obras activas;
- zonas rectangulares (o conjuntos de rectángulos) alrededor de cada obra;
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

Objetivo: que las decisiones de material, prisa y constructora tengan consecuencias.

Incluye:

- `TrackSpec` por celda o tramo;
- calidad de material y de ejecución;
- desgaste por uso;
- coste de mantenimiento;
- averías y reparaciones;
- save/load compatible;
- vías antiguas con calidad por defecto.

Esta fase debe existir antes de implementar materiales o ritmo manual. Sin mantenimiento, esos
valores no significan nada.

Depende de: ADR-039 y de la economía de obras de la Fase 2.

## Fase 5: materiales y ritmo manual

Objetivo: que construir a mano también tenga decisiones de calidad.

Incluye:

- material barato/estándar/premium;
- ritmo rápido/normal/cuidadoso;
- coste y tiempo distintos;
- calidad final combinada;
- UI para elegir y consultar.

Depende de: Fase 4.

## Fase 6: automatización por comando y 3D

Objetivo: exponer la misma maquinaria fuera de la UI 2D.

Incluye:

- comando `connect` con coordenadas/marcas/estaciones;
- planificar y encolar o solo planificar;
- posible `wait` para escenarios;
- HUD y zonas en 3D;
- sonido de obras y actividad visible en ambos clientes.

Depende de: lista, cola y constructoras.

## Decisiones que siguen abiertas

- Cifras de recargo automático, ticks base y cancelación.
- Si el presupuesto se reserva completo o solo se comprueba al arrancar.
- Si el manual puede quedar en negativo pagando materiales.
- Capacidades concretas de cada constructora.
- Cuántas cuadrillas hay y cómo se cobra la simultaneidad.
- Representación exacta de las zonas y su margen.
- Persistencia de la lista de obras en savegames.
- Fórmula de calidad y ritmo de mantenimiento.

Estas decisiones no bloquean la Fase 1. Se pueden resolver cuando se llegue a cada fase, con datos
reales de uso.
