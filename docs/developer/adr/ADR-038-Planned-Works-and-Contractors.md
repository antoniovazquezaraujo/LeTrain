# ADR-038: Planes de obra, presupuestos y constructoras

## Estado: PROPUESTO (fases 1 y primer corte de fase 2 implementados)

> La **lista de obras en fase 1** ya está implementada: guarda borradores con origen, destino,
> política, `RoutePlan`, coste y tiempo estimados, y permite añadirlos, editarlos, eliminarlos y ver
> sus totales. La edición abre el plan guardado y solo permite cambiar su política; los extremos
> quedan fijos.
>
> De la **fase 2** ya existen el catálogo de constructoras y las cotizaciones por plan, con coste,
> tiempo, calidad y capacidades. `Q` compara ofertas y la lista muestra la compañía/calidad
> seleccionada. **No** hay todavía efectivo, reserva, penalización de cancelación ni `Run`; las
> zonas, la calidad real y la ejecución siguen fuera. La cola actual (ADR-037) sigue construyendo
> inmediatamente cuando se acepta un plan desde `Shift+C`; `Run` está deshabilitado en la lista.
>
> Depende de ADR-034, ADR-035, ADR-036 y ADR-037. La calidad de construcción y el mantenimiento se
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
- cambiar calidad y velocidad de construcción;
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
- coste de construcción;
- coste de la constructora;
- coste total;
- tiempo estimado;
- calidad/velocidad elegidas;
- estado: borrador, presupuestada, contratada, en ejecución, terminada o cancelada;
- zona de exclusión asociada cuando se ejecute.

La lista debe mostrar el total de las obras seleccionadas. El dueño quiere poder comparar y
priorizar antes de gastar.

La fase 1 implementa un subconjunto: origen, destino, política, `RoutePlan`, coste y tiempo
estimados, con estado `DRAFT`. Los campos de constructora, cotizaciones, calidad, velocidad y zona
pertenecen a fases posteriores; no se muestran todavía en la tabla. Al guardar una ruta se crean
las marcas `Way-<id>-start` y `Way-<id>-end` en sus extremos.

### 3. Constructoras y cotizaciones

Las rutas no las construye una abstracción genérica: las ofrece una **compañía constructora**. Cada
compañía puede tener:

- nombre y descripción;
- tarifas o multiplicadores de precio;
- velocidad de ejecución;
- calidad de construcción (materiales, mano de obra y dirección como un paquete);
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
  constructionCost
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

Cuando una obra empieza, se reserva una zona alrededor de su ruta. Para la primera versión se ha
acordado un **único rectángulo simple** por obra, con un color propio para identificarlo en el
mapa. Ese rectángulo:

- cubre la ruta completa y un margen de seguridad;
- impide que otra obra automática construya dentro;
- impide que el usuario construya o borre manualmente dentro;
- se dibuja en el mapa;
- se libera cuando la obra termina o se cancela.

La intención es que el jugador pueda construir manualmente **fuera** de la zona mientras la obra
avanza. El margen evita que una construcción exterior se acople sin querer a la obra.

La posibilidad de usar varios rectángulos por obra queda descartada para la primera versión. Si
una ruta muy diagonal hace que el rectángulo reserve demasiado terreno, se podrá revisar más
adelante, pero no se implementará ahora.

### 5. Orden y simultaneidad

El dueño podrá ordenar la lista de obras. Ejecutar varias a la vez o en un orden concreto puede
tener distinto precio y duración. Se ha acordado que habrá un límite de **cuatro equipos** de
construcción y que la simultaneidad se cobrará **mucho más cara** que ejecutar las obras en
secuencia. La idea es que el jugador pueda acelerar, pero que no sea gratis.

Las preguntas que quedan abiertas son las cifras exactas:

- cómo se reparte el coste extra entre la segunda, tercera y cuarta obra simultánea;
- si el tiempo total de varias obras es el máximo de sus plazos o una combinación de ejecución
  secuencial y paralela;
- si se puede cambiar el orden mientras algunas ya están en marcha;
- si una obra puede esperar a que se libere un equipo o hay que cancelarla.

La primera versión permitirá varias obras **solo si sus zonas no se solapan** y solo hasta el
límite de equipos disponibles.

### 6. Efectivo y reserva

Para empezar una obra subcontratada, el jugador debe disponer de efectivo suficiente. La regla
acordada es:

- no se puede contratar si el saldo disponible no cubre el presupuesto;
- el presupuesto se reserva **al arrancar la obra**;
- el dinero de las obras ya en marcha cuenta como reservado y no se puede volver a usar;
- si se lanzan varias obras, se comprueba la suma de sus reservas;
- la reserva se consume celda a celda;
- al cancelar, se devuelve lo no consumido después de aplicar la penalización;
- la obra automática no debe dejar por sí sola un saldo negativo.

El modo manual sigue disponible si no hay dinero para subcontratar. Se ha acordado que el manual
**sí puede quedar en negativo pagando materiales**: el jugador se ahorra la constructora y la mano
de obra, pero el material se sigue comprando. La reserva de las obras automáticas, en cambio, no
puede estar descubierta.

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

La velocidad manual y la calidad de construcción se detallan en
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

Más adelante, la misma maquinaria podrá exponerse como comando de autoría. Se ha acordado que
habrá **un único comando `connect`**, no dos comandos separados para planificar y construir. El
comando tendrá que ser más rico que el borrador inicial porque, si automatiza directamente sin
pasar por la lista, necesita saber con qué empresa, calidad y ritmo se construye. El dueño ha
descartado los materiales como parámetro separado: calidad y ritmo resumen el paquete.

Una forma posible, todavía no cerrada, sería:

```text
connect <origen> to <destino> <política>
        [company <constructora>]
        [quality <nivel>]
        [pace <ritmo>]
```

Los parámetros de empresa/calidad/ritmo podrían tener valores por defecto para que el comando
siga siendo cómodo. Cada empresa ofrecerá su propia calidad y ritmo a un precio, y algunas
empresas baratas no harán túneles ni puentes. También podría existir una variante con espera para
escenarios.

La lista de presupuestos **no es un requisito para automatizar**: es una herramienta de gestión y
comparación. `connect` puede planificar, comprobar efectivo y encolar una obra directamente. La
lista sirve para preparar y comparar varias obras antes de lanzarlas, no para que el comando
funcione. No forma parte de esta ADR.

## Fuera de alcance

- Implementar constructoras, cotizaciones, zonas o el pago por adelantado (la lista de borradores
  de fase 1 ya existe).
- Cambiar la cola actual de una obra.
- Calidad real y mantenimiento (ADR-039).
- Comando `connect`.
- Integración 3D.

## Consecuencias

- La preview y la cola actual siguen siendo válidas como base.
- El `RoutePlan` no cambia; la cotización y el presupuesto son capas posteriores.
- Las zonas de exclusión permiten varias obras y edición manual fuera sin tocar el planner.
- El efectivo y la cancelación convierten la lista en una herramienta económica, no solo visual.
- La calidad de constructora solo tendrá sentido cuando exista mantenimiento.

## Decisiones abiertas

- Cifras de recargo automático, tiempo base y cancelación.
- Cómo se reparte el sobrecoste entre las obras simultáneas (límite acordado: cuatro equipos).
- Capacidades exactas de cada constructora.
- Persistencia de la lista de obras en savegames.
- Orden de implementación entre lista, constructoras y zonas.
- Sintaxis final y valores por defecto de `connect`.
- Posible comando `construct` futuro para ejecutar una lista de trayectos de forma simultánea,
  seleccionada u ordenada. No se implementa ahora.
- Posible sistema bancario/créditos para financiar obras. No se implementa ahora.

Las decisiones ya cerradas son: reserva de presupuesto al arrancar, el manual puede quedar en
negativo pagando materiales, una zona rectangular simple por obra y un límite de cuatro equipos
con simultaneidad más cara. La calidad es un paquete único (materiales, mano de obra y dirección);
no hay una elección separada de materiales.
