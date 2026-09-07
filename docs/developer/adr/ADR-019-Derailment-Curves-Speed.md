# ADR-019: Descarrilamiento por densidad de curvas y velocidad (issue #350)

## Estado: PROPUESTO

## Contexto
Los trenes ya respetan las señales de límite de velocidad (`SpeedSignal`), pero no existe una consecuencia mecánica natural por ignorarlas fuera de chocar contra otro tren. Queremos que las señales de velocidad tengan propósito: forzar a frenar antes de tramos sinuosos o forks.

La propuesta (issue #350) es que un tren **descarrile** si toma demasiadas curvas a alta velocidad. Para evitar explotaciones del tipo "curva-recta-curva a máxima velocidad", el riesgo no debe depender de un contador simple de curvas consecutivas, sino de la **densidad de curvas** en una ventana temporal/espacial proporcional a la velocidad.

Nota de contexto del equipo: la mecánica debe **mantenerse simple** y acotada; no debe tocar la lógica de bloques/cantones ni los loops de reserva.

## Decisión
1. **Nueva causa de muerte**: un tren puede pasar a `TrainState.DEAD` por **descarrilamiento** (además de por colisión). El tren descarrilado se elimina con el mismo flujo que un tren accidentado (evento, limpieza, economía).

2. **Riesgo por densidad de curvas (solo se evalúa al entrar en una curva)**:
   - El descarrilamiento solo puede ocurrir **al entrar en una curva** (una recta por sí sola no es peligrosa), así que la evaluación de riesgo vive únicamente en el momento de pisar una curva. Eso elimina trabajo por cada raíl.
   - **Qué es una curva**: en este motor no existe un tipo `Curve`; las curvas son `RailTrack` normales cuyo router gira. Como al mover el tren ya calculamos el rumbo de salida de cada pieza (`next.getDir(entryPort)`), una pieza es curva si `rumboDeSalida != rumboConElQueSeEntró` (comparación booleana de dirs que ya tenemos). **No se miden ángulos** y no se compensan las "eses": una curva es una curva, aunque la siguiente corrija la dirección.
   - **Historial por tren**: al entrar en cada curva se anota la separación (número de rectas) desde la curva anterior en un pequeño anillo de curvas recientes. El coste en rectas es solo incrementar un contador (`straightsSinceLastCurve`) dentro del bucle de movimiento, que ya hace trabajo por tile — despreciable.
   - **Ventana proporcional a la velocidad (inercia)**: `N` (número de piezas que "recuerda" el tren) es proporcional a la velocidad actual (p. ej. `N = round(speed)`). Al frenar, la ventana se encoge (se descarta historia antigua); al estar parado, se vacía. Así, frenar antes de la zona sinuosa reduce la memoria de curvas acumulada.
   - **Métrica de peligro**: al entrar en una curva se cuenta cuántas curvas recientes caen dentro de los últimos `N` raíles recorridos. Si superan un límite seguro para esa velocidad (p. ej. `curvas >= N/2`, configurable), el tren descarrila. Esta evaluación solo ocurre en curvas (O(curvas recientes), evento poco frecuente).
   - **Velocidad mínima de descarrilamiento**: por debajo de una velocidad mínima (`derail.minSpeed`, configurable) el tren **nunca descarrila**, sin importar cuántas curvas encadene (a baja velocidad la inercia lateral es despreciable). La tabla de balance solo aplica a velocidades por encima de ese umbral; por debajo, las curvas permitidas son ilimitadas.
   - Sin cambios en loops de tick de bloques/seguridad ni en reservas.

3. **Desvíos (Forks) como zona intrínsecamente peligrosa**:
   - Independientemente de la ventana de curvas, atravesar un `ForkRailTrack` tiene un **límite de velocidad duro**.
   - Si la cabeza cruza un desvío a una velocidad superior a ese límite, descarrila **inmediatamente**, aunque el recorrido por el desvío sea recto.

4. **Configuración**: los parámetros (velocidad mínima de descarrilamiento, factor de ventana `N`, tabla de curvas permitidas por velocidad, límite duro de desvío) se exponen en `economy.properties` para ajuste de balance sin recompilar.

5. **Marcha atrás y trenes largos**: la marcha atrás se rige por la misma regla que la marcha normal (el historial se recorre en orden inverso). Si el tren descarrila, lo hace al completo (cabeza, vagones y carga), igual que un accidente.

6. **No regresión**: la mecánica convive con la física y señales actuales; un tren que respeta las señales (o autopilot/seguridad que frena) no debe descarrilar en trazados normales.

## Consecuencias
- Las señales de velocidad (`SpeedSignal`) pasan a tener un propósito mecánico claro: anticipar el frenado antes de tramos sinuosos o forks.
- Nueva vía de daño/economía (el descarrilamiento destruye el tren y su carga, como un accidente).
- El autopilot/itinerarios no requieren cambios de arquitectura: si la ruta pasa por curvas, el comportamiento se regula por velocidad y señales como hoy.
- Tests: casos de tabla deterministas (velocidad + geometría + historial → descarrila o no), incluidos: curva suelta a alta velocidad, zigzag a alta velocidad, zigzag a baja velocidad (no descarrila), frenado antes de la zona sinuosa, y límite duro de desvío.

## Puntos abiertos (a trabajar en este ADR)
- Fórmula y umbrales exactos (tabla de balance): valores de `derail.minSpeed`, de `N` por velocidad y de curvas permitidas por velocidad.
- Ubicación del historial/anillo: **`SafetyManager`** del tren. Se resetea cuando el tren se detiene o invierte la marcha. 
- Verificar interacción con el respeto actual de `SpeedSignal`: una señal frena al tren automáticamente, pero **el jugador puede volver a acelerar después de pasarla** (conducción manual). Ese es el comportamiento que castiga el descarrilamiento: quien respeta la señal (no vuelve a acelerar) atraviesa la zona sinuosa por debajo del umbral y está seguro; quien re-acelera llega a la curva a alta velocidad y descarrila. Para que esto funcione, la evaluación debe usar la **velocidad efectiva en el instante de entrar en la curva** (ya con las frenadas/aceleraciones aplicadas por señales y por el jugador), nunca una velocidad objetivo o previa. Así no existe "doble castigo": respetar la señal (no re-acelerar) siempre es seguro.

## Fuera de alcance (ideas futuras, no forman parte de esta mecánica)
- **Ayuda visual al construir**: que el cursor cambie de color al trazar curvas muy seguidas (con un último estado rojo intermitente de máximo peligro) para avisar al jugador de que está creando un tramo sinuoso. Es una feature de UX/construcción independiente del descarrilamiento; se trataría por separado.

