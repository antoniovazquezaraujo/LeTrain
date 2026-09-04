# ADR-018: Renderizado 2D de Entidades de Vía

## Contexto
Actualmente, las entidades de vía (sensores, señales, semáforos y estaciones) tienen un comportamiento visual inconsistente en la vista 2D (Terminal). 
- Algunos cambian de color al seleccionarse, lo que puede confundirse con su estado lógico o físico (ej. un semáforo en rojo o verde).
- A menudo se renderizan encima de la vía, ocultando la forma del trazado y generando ruido visual.

## Decisión
Para unificar la interfaz de usuario en la terminal 2D (Lanterna) y mejorar la claridad del mapa, se adoptan las siguientes convenciones de renderizado para todos los componentes de vía (`TrackComponent`: Sensor, SpeedSignal, RailSemaphore, Station):

1. **Indicador de Selección**: 
   - Cuando un componente se encuentre seleccionado (modo activo), **no** cambiará su color.
   - En su lugar, la id del componente se mostrará **subrayada**. 
   - Esto preserva el color original de la entidad, que puede estar utilizándose para transmitir información de estado (como la ocupación de un cantón o el estado de un semáforo).

2. **Posicionamiento Relativo (Offset)**:
   - Los componentes de vía dejarán de pintarse directamente en las mismas coordenadas espaciales exactas que el segmento de vía.
   - Se situarán en la celda adyacente al **lado derecho** de la vía, calculando el "derecho" en función del **sentido del cursor en el momento de la creación**.
   - Además, las entidades semaphore, signal y sensor pueden invertirse para que actúen solamente en la dirección que el usuario decida. 
   - En el modo de cada entidad, deben mostrar una flecha que indique la dirección. En el funcionamiento normal, la flecha no debería mostrarse, se deja solo para efectos de aclarar el sentido en vías problemáticas, que puedan estar muy juntas y generar confusión con qué lado es cual.
   - Esta separación permite que la vía subyacente siga siendo visible, funcionando visualmente como un elemento de infraestructura montado al margen de los rieles.
   - En el modo de cada entidad, cuando la entidad se selecciona, la id ha de aparecer subrayada, y las demás sin subrayar. En modo normal no debe mostrarse la id. 
   - Los forks deberían también ajustarse a este estilo, mostrando solamente la id subrayada y sin cambiar de color.
   - Tanto las flechas como las id deberían ser de color gris.
   - Cuando se selecciona una entidad, no hay por qué centrar la pantalla en ella. Solo ha de hacerse si esa entidad se sale del rectángulo de cámara.

## Consecuencias
- La capa de presentación (`RenderVisitor` o equivalente en la terminal) deberá encargarse de calcular este desplazamiento lateral (+1/-1 en X o Y) en función del vector de dirección del `RailTrack` (ej. Norte-Sur, Este-Oeste, etc.).
- Será necesario utilizar el soporte de texto subrayado que provee Lanterna para resaltar la selección en la consola.
- La interfaz 2D ganará legibilidad y profesionalidad, acercándose a los planos reales de infraestructura ferroviaria.

