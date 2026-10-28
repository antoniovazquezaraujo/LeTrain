# ADR-039: Calidad de vía y mantenimiento

## Estado: PROPUESTO

> Esta ADR recoge ideas para una fase posterior. **No está implementada.** Hoy no existe
> mantenimiento de vías ni calidad de construcción; el `RailTrack` solo distingue vía normal,
> puente y túnel.
>
> Depende de [[ADR-037-Construction-Queue|ADR-037]] y de
> [[ADR-038-Planned-Works-and-Contractors|ADR-038]].

## Contexto

La construcción manual actual usa un coste fijo por tipo de vía y una vía normal se coloca sin
espera. El dueño quiere que el jugador pueda decidir **cómo** construye, no solo **por dónde**:

- construir rápido o con cuidado;
- obtener una vía que dure más o que se degrade antes;
- que una constructora tenga su propia calidad de ejecución.

En una primera aproximación se pensó en separar “material” y “ritmo” como dos variables. El dueño
ha simplificado la idea: **no habrá una elección separada de materiales**. Habrá un único concepto
de **calidad de construcción** que engloba los buenos materiales, la mano de obra profesional y una
dirección de obra cuidadosa. El jugador decide cuánto dinero pone en calidad y cuánto en rapidez;
si quiere ambas cosas a la vez, será muy caro.

Sin mantenimiento, la calidad sería una etiqueta sin efecto, así que esta ADR no se implementa
todavía.

## Decisiones y propuestas

### 1. `TrackSpec` por celda o tramo

Cada vía construida guardará, además del tipo visual actual:

```text
TrackSpec
  trackType     // normal, puente, túnel
  quality       // calidad global: materiales + mano de obra + dirección
  buildPace     // rápido, normal, cuidadoso (si se conserva como eje separado)
```

La calidad puede guardarse por celda o agregarse por tramo. Para mezclar secciones de distinta
calidad, lo más flexible es guardarla por celda y agregarla al mostrar mantenimiento.

Las vías antiguas, las de savegames y las ya construidas tendrán un `TrackSpec` por defecto. La
compatibilidad de save/load es obligatoria.

### 2. Calidad de construcción

La calidad es un **paquete**, no una lista de materiales:

- buenos materiales;
- mano de obra profesional;
- dirección de obra comprometida;
- ejecución cuidadosa.

El jugador no elige “acero A o acero B”; elige un nivel de calidad y paga por él. La constructora
ofrece su propio paquete de calidad, materiales y ejecución.

La calidad afecta al coste inicial y, sobre todo, al mantenimiento futuro:

- desgaste por uso;
- coste de mantenimiento;
- probabilidad de avería o cierre;
- posibles límites de velocidad;
- reparaciones.

El objetivo de juego es que una vía barata y rápida no sea una ganga a largo plazo. Una vía cara y
cuidadosa tarda más y cuesta más al principio, pero da menos problemas.

### 3. Rapidez de construcción

La rapidez es un eje distinto del precio:

- construir rápido ahorra tiempo;
- construir con calidad cuesta dinero;
- construir rápido **y** con calidad debe ser carísimo.

No se infiere la calidad por la velocidad real del cursor, porque sería impredecible y difícil de
explicar. El jugador elige un ritmo explícito o contrata una empresa con un ritmo conocido. La
calidad no cambia por el ritmo sin que el jugador lo sepa.

La relación exacta entre precio, calidad y tiempo queda abierta, pero la intención es clara: el
dinero se gasta o en calidad o en rapidez, y conseguir ambas cosas es un lujo.

### 4. Constructoras

Cada constructora tendrá su propio paquete de calidad y rapidez, además de precio, plazo y
capacidades (ADR-038). Una constructora barata puede usar materiales modestos y ejecución rápida;
una constructora internacional puede ofrecer calidad alta y plazos mejores.

El jugador contrata la calidad como parte del servicio, no solo el trazado. Una empresa barata
puede no hacer túneles o puentes.

### 5. Mantenimiento

El sistema de mantenimiento debe consumir `TrackSpec` y el uso real:

- desgaste por paso de trenes;
- coste de mantenimiento periódico;
- probabilidad de avería o cierre;
- posible límite de velocidad por calidad;
- reparaciones que devuelven condición;
- prioridad de mantenimiento por tramo.

La implementación debe ser determinista con la semilla del mundo; nada de averías aleatorias no
reproducibles.

### 6. UI

La UI debe permitir:

- elegir calidad y rapidez antes de construir;
- ver el coste y tiempo resultantes;
- ver la calidad estimada;
- ver el estado/desgaste de un tramo;
- recibir avisos de mantenimiento y averías.

No se debe obligar al jugador a recordar qué calidad puso en cada celda: la información debe estar
en el mapa o en un panel consultable.

### 7. Relación con las obras automáticas

Una obra automática tendrá un `TrackSpec` derivado de la constructora y del presupuesto. El jugador
puede elegir una constructora por calidad/precio/plazo, pero no debería poder cambiar el material
celda a celda durante la obra sin cambiar el presupuesto.

La calidad de la constructora y la calidad manual deben usar el mismo modelo de datos para que el
mantenimiento no tenga dos caminos distintos.

## Fuera de alcance

- Implementar `TrackSpec`, calidad o mantenimiento ahora.
- Cambiar el coste fijo actual de las vías antes de que exista mantenimiento.
- Añadir campos a `RailTrack` sin un plan de compatibilidad de save/load.
- Cambiar la cola básica o las constructoras de ADR-038.

## Consecuencias

- El diseño de vías gana profundidad sin tocar el planner.
- El coste inicial y el coste futuro quedan conectados.
- La decisión manual “rápido y barato” frente a “lento y bueno” tiene sentido.
- El mantenimiento se convierte en un sistema de juego real, no en una etiqueta.
- Requiere una fase propia y pruebas de save/load; no debe colarse en la cola básica.

## Decisiones abiertas

- Fórmula exacta de calidad y su relación con precio/tiempo.
- Niveles de calidad y precios.
- Si “calidad” y “rapidez” son dos ejes separados o una sola elección de paquete.
- Ticks extra del modo cuidadoso.
- Cómo se guarda la calidad por celda/tramo y cómo se migran saves.
- Coste/frecuencia del mantenimiento y probabilidad de avería.
- Si la calidad afecta a velocidad máxima, capacidad o solo mantenimiento.
- Cómo se muestra el desgaste en 2D y 3D.
