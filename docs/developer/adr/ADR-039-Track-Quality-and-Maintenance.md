# ADR-039: Calidad de vía, materiales y mantenimiento

## Estado: PROPUESTO

> Esta ADR recoge ideas para una fase posterior. **No está implementada.** Hoy no existe
> mantenimiento de vías ni calidad de materiales; el `RailTrack` solo distingue vía normal, puente
> y túnel. Cualquier etiqueta de calidad sin mantenimiento sería decorativa, así que no se implementa
> todavía.
>
> Depende de [[ADR-037-Construction-Queue|ADR-037]] y de
> [[ADR-038-Planned-Works-and-Contractors|ADR-038]] para el contexto de obras y constructoras.

## Contexto

La construcción manual actual usa un coste fijo por tipo de vía y una vía normal se coloca sin
espera. El dueño quiere que el jugador pueda decidir **cómo** construye, no solo **por dónde**:

- usar materiales baratos o caros;
- construir rápido o con cuidado;
- obtener una vía que dure más o que se degrade antes;
- que una constructora tenga su propia calidad y materiales.

Eso conecta directamente con un futuro sistema de mantenimiento. Sin él, elegir “acero bueno” no
tendría ninguna consecuencia y sería una decisión falsa.

## Decisiones y propuestas

### 1. `TrackSpec` por celda o tramo

Cada vía construida guardará, además del tipo visual actual:

```text
TrackSpec
  trackType        // normal, puente, túnel
  materialGrade    // barato, estándar, premium
  buildPace        // rápido, normal, cuidadoso
  quality          // resultado final, derivado
```

La calidad puede guardarse por celda o agregarse por tramo. Para mezclar secciones de distinta
calidad, lo más flexible es guardarla por celda y agregarla al mostrar mantenimiento.

Las vías antiguas, las de savegames y las ya construidas tendrán un `TrackSpec` por defecto
(estándar/normal). La compatibilidad de save/load es obligatoria.

### 2. Materiales

Tres niveles iniciales, ajustables:

- **Barato:** coste bajo, durabilidad baja, mantenimiento frecuente.
- **Estándar:** referencia.
- **Premium:** coste alto, durabilidad alta, mantenimiento menos frecuente.

El material afecta al coste de construcción. No todos los materiales tienen por qué servir para
todo:

- un puente puede exigir un mínimo de calidad;
- un túnel puede exigir materiales y una constructora con capacidad;
- una vía normal puede aceptar cualquier grado.

La lista de materiales debe ser datos, no una cadena de `if` repartida por el código.

### 3. Ritmo de construcción manual

En lugar de inferir la calidad por la velocidad real del cursor (impredecible y difícil de
explicar), el jugador elige un ritmo explícito:

- **Rápido:** sin espera extra, calidad de ejecución baja.
- **Normal:** un pequeño coste temporal, calidad estándar.
- **Cuidadoso:** más ticks por celda, calidad alta.

El coste de materiales no cambia por el ritmo; lo que cambia es el tiempo y la calidad de
ejecución. En rutas largas, construir cuidadosamente será más pesado para el jugador, que es
precisamente el intercambio. El manual sigue pagando los materiales aunque el saldo quede en
negativo: no hay recargo de constructora, pero el material no es gratis. La reserva de presupuesto
solo se aplica a las obras automáticas.

La calidad final se obtiene combinando material y ejecución. La fórmula exacta queda abierta, pero
debe ser determinista y fácil de explicar en la UI.

### 4. Constructoras

Cada constructora tendrá su propio perfil de material, ritmo y calidad, además de precio, plazo y
capacidades (ADR-038). Una constructora barata puede usar material barato y ejecución rápida; una
constructora internacional puede usar material premium y ejecución cuidadosa.

El jugador podrá contratar la calidad como parte del servicio, no solo el trazado.

### 5. Mantenimiento

El sistema de mantenimiento debe consumir `TrackSpec` y el uso real:

- desgaste por paso de trenes;
- coste de mantenimiento periódico;
- probabilidad de avería o cierre;
- posible límite de velocidad por calidad;
- reparaciones que devuelven condición;
- prioridad de mantenimiento por tramo.

El objetivo de juego es que una vía barata y rápida no sea una ganga a largo plazo: ahorra al
construir, pero cuesta más mantenerla o se estropea antes. Una vía cara y cuidadosa tarda más y
cuesta más al principio, pero da menos problemas.

La implementación debe ser determinista con la semilla del mundo; nada de averías aleatorias no
reproducibles.

### 6. UI

La UI debe permitir:

- elegir material y ritmo antes de construir;
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

- Implementar `TrackSpec`, materiales, ritmo o mantenimiento ahora.
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

- Fórmula exacta de calidad a partir de material y ritmo.
- Niveles y precios de materiales.
- Ticks extra del modo cuidadoso.
- Cómo se guarda la calidad por celda/tramo y cómo se migran saves.
- Coste/frecuencia del mantenimiento y probabilidad de avería.
- Si la calidad afecta a velocidad máxima, capacidad o solo mantenimiento.
- Cómo se muestra el desgaste en 2D y 3D.
