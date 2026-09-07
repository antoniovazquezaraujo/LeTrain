# ADR-019: Descarrilamiento por densidad de curvas y velocidad (issue #350)

## Estado: PROPUESTO

## Contexto
Los trenes ya respetan las señales de límite de velocidad (`SpeedSignal`), pero no existe una consecuencia mecánica natural por ignorarlas fuera de chocar contra otro tren. Queremos que las señales de velocidad tengan propósito: forzar a frenar antes de tramos sinuosos o de montaña.

La propuesta (issue #350) es que un tren **descarrile** si toma demasiadas curvas a alta velocidad. Para evitar explotaciones del tipo "curva-recta-curva a máxima velocidad", el riesgo no debe depender de un contador simple de curvas consecutivas, sino de la **densidad de curvas** en una ventana temporal/espacial proporcional a la velocidad.

Nota de contexto del equipo: la mecánica debe **mantenerse simple** y acotada; no debe tocar la lógica de bloques/cantones ni los loops de reserva.

## Decisión
1. **Nueva causa de muerte**: un tren puede pasar a `TrainState.DEAD` por **descarrilamiento** (además de por colisión). El tren descarrilado se elimina con el mismo flujo que un tren accidentado (evento, limpieza, economía).

2. **Ventana deslizante por tren (Inertial Sliding Window)**:
   - Cada tren mantiene un historial de las últimas `N` piezas de vía que ha atravesado su **cabeza**.
   - `N` es proporcional a la velocidad actual del tren (p. ej. `N = round(speed)`). Al frenar, la ventana se encoge (se descarta historia antigua); al estar parado, la ventana se vacía. Esto materializa la "inercia": frenar antes de la zona peligrosa reduce la memoria de curvas.
   - Una pieza cuenta como **curva** si atravesarla cambia el rumbo (ángulo de giro >= umbral, usando la métrica de `Dir`); los tramos rectos no cuentan.
   - **Métrica de peligro**: se cuenta el número de curvas dentro de la ventana. Si supera un límite seguro para esa velocidad (p. ej. `curvas >= N/2`, configurable), el tren descarrila.
   - La evaluación se hace solo cuando la cabeza entra en una pieza nueva, con coste amortizado O(1) (cola + contador). Sin cambios en loops de tick de bloques/seguridad.

3. **Desvíos (Forks) como zona intrínsecamente peligrosa**:
   - Independientemente de la ventana de curvas, atravesar un `ForkRailTrack` tiene un **límite de velocidad duro**.
   - Si la cabeza cruza un desvío a una velocidad superior a ese límite, descarrila **inmediatamente**, aunque el recorrido por el desvío sea recto.

4. **Configuración**: los parámetros (factor de ventana `N`, umbral de curvas, umbral de ángulo de curva, límite duro de desvío) se exponen en `economy.properties` para ajuste de balance sin recompilar.

5. **No regresión**: la mecánica convive con la física y señales actuales; un tren que respeta las señales (o autopilot/seguridad que frena) no debe descarrilar en trazados normales.

## Consecuencias
- Las señales de velocidad (`SpeedSignal`) pasan a tener un propósito mecánico claro: anticipar el frenado antes de tramos sinuosos.
- Nueva vía de daño/economía (el descarrilamiento destruye el tren y su carga, como un accidente).
- El autopilot/itinerarios no requieren cambios de arquitectura: si la ruta pasa por curvas, el comportamiento se regula por velocidad y señales como hoy.
- Tests: casos de tabla deterministas (velocidad + geometría + ventana → descarrila o no), incluidos: curva suelta a alta velocidad, zigzag a alta velocidad, zigzag a baja velocidad (no descarrila), frenado antes de la zona sinuosa, y límite duro de desvío.

## Puntos abiertos (a trabajar en este ADR)
- Fórmula y umbrales exactos (tabla de balance): valores de `N`, `curvas >= umbral`, ángulo mínimo de "curva" (¿45°?, ¿90°?).
- Comportamiento con marcha atrás y con trenes largos (¿solo la cabeza?, ¿colas de vagones?).
- Dónde vive el historial (¿`Train` o `SafetyManager`?) y su reseteo (parada, inversión, teletransporte).
- Eventos/DSL: ¿evento `on derail` para scripts, o reutilizar `crash`/`contact`?
- Feedback visual/HUD cuando el tren se acerca al límite (opcional).
- Verificar interacción con el respeto actual de `SpeedSignal` (no duplicar castigos).
