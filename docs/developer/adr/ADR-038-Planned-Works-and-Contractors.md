# ADR-038: Planes de obra, presupuestos y constructoras

## Estado: PROPUESTO

> Esta ADR recoge ideas acordadas para una fase posterior. **No está implementada.** La cola actual
> construye inmediatamente después de seleccionar un plan y no tiene lista, constructoras ni
> presupuestos (ver [[ADR-037-Construction-Queue|ADR-037]]).
>
> Depende de ADR-034, ADR-035, ADR-036 y ADR-037. La calidad de materiales y el mantenimiento se
> separan en [[ADR-039-Track-Quality-and-Maintenance|ADR-039]].

## Contexto

Hoy el autor elige una ruta y la construye en el acto. Es cómodo para una obra suelta, pero no
permite preparar varias rutas, comparar precios ni decidir cuándo se ejecutan. El dueño quiere que
la planificación y la ejecución sean dos actividades distintas:

- primero se estudian y presupuestan varias rutas;
- después se decide cuáles se contratan, en qué orden y con qué empresa;
- mientras unas se construyen, el jugador puede seguir planificando o construyendo manualmente en
  otras zonas.

Esto también abre la puerta a que distintas empresas ofrezcan precios, plazos, calidades y
capacidades diferentes. La idea encaja con una economía de autoría: el jugador trabaja poco, pero
paga por delegar.

## Decisiones y propuestas

### 1. Planificación separada de ejecución

En el futuro, aceptar una ruta en la preview no la construirá inmediatamente. La añadirá a una
**lista de obras presupuestadas**. Desde esa lista se podrá:

- revisar la ruta y sus números;
- volver a editarla;
- cambiar la política;
- cambiar materiales y velocidad de construcción;
- pedir precios a una o varias constructoras;
- eliminarla;
- ordenarla respecto a otras obras;
- decidir si se ejecuta sola, en un orden concreto o simultáneamente con otras.

La construcción inmediata seguirá existiendo como opción, pero dejará de ser el único flujo.

### 2. Lista de obras

Cada entrada de la lista debe guardar, como mínimo:

- origen y destino;
- política y `RoutePlan` actual;
- empresa seleccionada o cotizaciones disponibles;
- coste de materiales;
- coste de la constructora;
- coste total;
- tiempo estimado;
- calidad/material/velocidad elegidos;
- estado: borrador, presupuestada, contratada, en ejecución, terminada o cancelada;
- zona de exclusión asociada cuando se ejecute.

La lista debe mostrar el total de las obras seleccionadas. El dueño quiere poder comparar y
priorizar antes de gastar.

### 3. Constructoras y cotizaciones

Las rutas no las construye una abstracción genérica: las ofrece una **compañía constructora**. Cada
compañía puede tener:

- nombre y descripción;
- tarifas o multiplicadores de precio;
- velocidad de ejecución;
- calidad de materiales/ejecución;
- capacidades: puentes, túneles, longitud máxima, tipo de terreno;
- política de cancelación;
- número de cuadrillas o trabajos simultáneos.

Ejemplos de tono, no cifras definitivas:

- **Benito, Chapuzas a Domicilio:** barato, rápido, poca calidad, no hace túneles ni puentes.
- **Construcciones Internacionales S.A.:** caro, más lento, buena calidad, puentes y túneles.

Para cada plan se generará una **cotización**:

```text
WorkQuote
  company
  routePlan
  materialCost
  companyPremium
  totalCost
  totalTicks
  feasible
  rejectionReason
  cancellationPolicy
```

Una empresa sin capacidad para un puente o túnel debe marcar la cotización como no viable
**antes** de contratar. No se debe aceptar una obra y descubrir el problema al empezar a construir.

### 4. Zonas de exclusión

Cuando una obra empieza, se reserva una zona alrededor de su ruta. La zona:

- puede ser un rectángulo o un conjunto de rectángulos;
- debe cubrir la ruta y un margen de seguridad;
- impide que otra obra automática construya dentro;
- impide que el usuario construya o borre manualmente dentro;
- se dibuja en el mapa;
- se libera cuando la obra termina o se cancela.

La intención es que el jugador pueda construir manualmente **fuera** de la zona mientras la obra
avanza. El margen evita que una construcción exterior se acople sin querer a la obra.

Queda por decidir si el margen es siempre de una celda, si se puede configurar y si las reservas
se representan internamente como rectángulos, celdas exactas o ambas.

### 5. Orden y simultaneidad

El dueño podrá ordenar la lista de obras. Ejecutar varias a la vez o en un orden concreto puede
tener distinto precio y duración. Las preguntas abiertas son:

- ¿Cada obra tiene su propia cuadrilla o varias comparten capacidad?
- ¿Una obra simultánea cuesta más porque requiere más recursos?
- ¿El tiempo total es el máximo de las obras o la suma de una ejecución secuencial?
- ¿Se puede cambiar el orden mientras algunas ya están en marcha?

La primera versión puede permitir varias obras **solo si sus zonas no se solapan**, con una
cuadrilla independiente por obra. La limitación de cuadrillas puede llegar después.

### 6. Efectivo y reserva

Para empezar una obra subcontratada, el jugador debe disponer de efectivo suficiente. La idea
acordada es:

- no se puede contratar si el saldo no cubre el presupuesto;
- el presupuesto se reserva al arrancar;
- si se lanzan varias obras, se comprueba la suma de sus reservas;
- la reserva se consume celda a celda;
- al cancelar, se devuelve lo no consumido después de aplicar la penalización;
- la obra automática no debe dejar por sí sola un saldo negativo.

El modo manual sigue disponible si no hay dinero para subcontratar. Queda por decidir si el manual
paga materiales como ahora, pudiendo quedar en negativo, o si también exige efectivo.

### 7. Cancelación

Cada obra se puede cancelar por separado. Al cancelar:

- la obra se detiene;
- la vía construida hasta ese momento permanece;
- se cobra lo construido;
- la empresa cobra una **tasa adicional de cancelación**;
- se libera la zona de exclusión.

La tasa es una decisión de empresa. Como propuesta inicial: un porcentaje del presupuesto
restante con un mínimo. Las cifras concretas no están cerradas.

### 8. Automático frente a manual

Las obras subcontratadas serán, en general, **más lentas y más caras** que hacerlo a mano. Eso es
intencionado: el jugador paga por delegar el trabajo y por una ejecución de calidad. El manual es
más barato en mano de obra, pero exige tiempo del jugador.

La velocidad manual, los materiales y la calidad se detallan en
[[ADR-039-Track-Quality-and-Maintenance|ADR-039]].

### 9. Actividad visible y sonido

Mientras una obra está en marcha:

- el mapa muestra la zona reservada;
- se ve la vía creciendo;
- el HUD muestra el progreso;
- si el cursor se acerca a la obra, se puede escuchar el sonido de construcción.

El sonido no es un requisito de la primera rebanada de lista, pero debe quedar contemplado en la
experiencia final.

### 10. Comando `connect`

Más adelante, la misma maquinaria podrá exponerse como comando de autoría:

```text
connect <origen> to <destino> <política>
```

Podría aceptar coordenadas, marcas o estaciones, planificar y añadir a la lista o encolar
directamente. También podría existir una variante con espera para escenarios. No forma parte de
esta ADR.

## Fuera de alcance

- Implementar la lista, las constructoras, las zonas o el pago por adelantado.
- Cambiar la cola actual de una obra.
- Materiales, calidad real y mantenimiento (ADR-039).
- Comando `connect`.
- Integración 3D.

## Consecuencias

- La preview y la cola actual siguen siendo válidas como base.
- El `RoutePlan` no cambia; la cotización y el presupuesto son capas posteriores.
- Las zonas de exclusión permiten varias obras y edición manual fuera sin tocar el planner.
- El efectivo y la cancelación convierten la lista en una herramienta económica, no solo visual.
- La calidad de constructora solo tendrá sentido cuando exista mantenimiento.

## Decisiones abiertas

- Cifras de recargo, tiempo base y cancelación.
- Si el presupuesto se reserva completo o solo se comprueba al arrancar.
- Si el manual paga materiales en negativo.
- Capacidades exactas de cada constructora.
- Número de cuadrillas y coste de la simultaneidad.
- Representación de zonas y margen.
- Persistencia de la lista en savegames.
- Orden de implementación entre lista, constructoras y zonas.
