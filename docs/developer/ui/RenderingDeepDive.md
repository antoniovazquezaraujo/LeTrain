# Motor de Renderizado (Visitor Pattern)

El motor de renderizado de LeTrain está diseñado para ser agnóstico del motor gráfico subyacente. Se basa en el **Patrón Visitor** para que cada entidad sepa cómo dibujarse sin importar el contexto visual.

## Interfaz `Visitor`
Define el contrato para "visitar" cada tipo de entidad en el juego:
- `visitRailTrack(RailTrack)`
- `visitForkRailTrack(ForkRailTrack)`
- `visitLocomotive(Locomotive)`
- `visitWagon(Wagon)`
- `visitGroundMap(GroundMap)`

## Implementaciones de Renderizado
Existen dos implementaciones principales de este contrato:
1. **`letrain.visitor.gdx3d.Gdx3DRenderer`**: Utiliza LibGDX para renderizar modelos 3D (`ModelInstance`). Cada método `visit` utiliza sub-renderizadores especializados (`TrackRenderer`, `VehicleRenderer`, etc.) que seleccionan los modelos y los posicionan en el espacio 3D.
2. **`letrain.visitor.terminal.RenderVisitor`**: Utiliza Lanterna para dibujar en una terminal 2D con caracteres ASCII/UTF-8. Los métodos `visit` eligen el símbolo (e.g., `#` para vías, `T` para trenes) basándose en la orientación y el estado.

## Cómo Añadir una Nueva Entidad Visual
1. Crear la clase de la entidad (e.g., `SignalLight`).
2. Añadir el método `accept(Visitor)` a la entidad:
    ```java
    @Override
    public void accept(Visitor visitor) {
        visitor.visitSignalLight(this);
    }
    ```
3. Añadir `visitSignalLight(SignalLight)` a la interfaz `Visitor`.
4. Implementar el dibujado en todos los renderizadores disponibles.

## Interpolación visual continua

El renderer 3D (`VehicleRenderer`) usa un sistema de interpolación de dos fases para suavizar el movimiento entre celdas, ya que la simulación física solo actualiza posiciones en ticks discretos (~20 TPS):

- **Phase 1** (`progress < 0.5`): El vehículo se desplaza desde el centro de la celda actual hacia la salida usando una curva de Bézier cuadrática.
- **Phase 2** (`progress >= 0.5`): El vehículo entra en la celda siguiente, moviéndose desde la entrada hacia el centro.

El `progress` se calcula a partir de `turns` (contador de ticks hasta el siguiente movimiento físico) y `animationAlpha` (factor de interpolación entre frames de render).

## Colisiones y `canEnterNext`

Para evitar que los vehículos se dibujen dentro de celdas ocupadas por otros trenes durante la interpolación, `PathGeometry.calculateTwoStagePath()` recibe un parámetro `canEnterNext`:

- Si `canEnterNext == false`: el vehículo se queda en el centro de su celda actual sin desplazarse (Phase 1 con `t = 0.5f`, Phase 2 no se ejecuta).
- Si `canEnterNext == true`: interpolación normal.

El cálculo de `canEnterNext` usa un **lookahead encadenado**: desde la celda del vehículo, sigue la cadena de linkers del mismo tren hacia adelante hasta encontrar una celda libre (OK) u ocupada por otro tren (BLOQUEADO). Esto permite que un vagón al final de un tren sepa que la locomotora al frente está bloqueada, y no intente entrar visualmente en su celda. (Ver [ADR-007](../adr/ADR-007-Collision-Visual-Interpolation.md))

## Bypass cuando el tren está parado

Cuando `speed == 0` o `train.isStalled()`, el renderer no llama a `calculateTwoStagePath()` en absoluto: dibuja el vehículo directamente en el centro exacto de su celda (`getPosition() + 0.5`), igual que el renderer 2D.

## Ciclo día/noche

Ambos renderers leen la hora del `GameClock` del modelo y aplican la paleta compartida; el ratio
`getDayNightRatio()` (0 = pleno día, 1 = noche) sale de `SolarModel` (día del año, hora solar y
latitud mundial). El detalle de tokens y fases está en
[DayNight_Colors.md](../systems/DayNight_Colors.md) y la decisión de diseño en
[ADR-022](../adr/ADR-022-Game-Time.md).

- **3D**: `GraphicPresenter.updateDayNight()` aplica `VisualPalette` en cada tick a la luz ambiental,
  al sol direccional (dirección real desde `SolarModel`), al cielo/`VOID` (repartidos por la línea de
  horizonte con `glScissor`), al tablero y a los materiales de terreno, vía y trenes
  (`Gdx3DResourceContext.applyTerrainPalette`). Los avisos y resaltados conservan su color.
- **2D**: `RenderVisitor` resuelve `TerminalPalette` por **niveles** (20, con histéresis direccional)
  y pinta cada token; las celdas dentro del cono del faro (`Headlight`) mezclan su color nocturno con
  el diurno, incluido el fondo.
- **Faros**: `VehicleRenderer` publica las posiciones renderizadas de las locomotoras con luz y el
  presentador dibuja hasta 4 charcos suaves con falloff cuadrático por
  fragmento (`HeadlightGlows` + `HeadlightGlowRenderer`) en las más cercanas a la cámara, con
  opacidad `VisualPalette.lightsOnFactor(ratio)`. Sustituyen a los `PointLight` originales, que
  sobre el suelo de un quad por celda dibujaban la rejilla como manchas cuadradas (#690); el plano
  del charco se pinta por encima del balasto (y por debajo de vía y tren) para que las cajas por
  celda del balasto no lo recorten. Luces y haz solo se encienden por encima de
  `VisualPalette.LIGHTS_ON_RATIO` (0,1) y con el motor en marcha y la locomotora de cabeza
  (`isEngineOn() && isHeadLocomotive()`); en 2D el túnel oculto también apaga el haz.

## Ping de localización del cursor (#696)

El cursor a veces se pierde sobre terreno cargado (mar, montaña, estaciones) o de noche. Pulsar
`o` (la tecla **Locate** que ya existía) lanza un **ping de un solo disparo** en la posición del
cursor: si había un elemento seleccionado el cursor salta a él, y si no lo había (modo Rails) el
ping marca el cursor donde está. El estado vive en `letrain.vehicle.Cursor`
(`ping(now)` / `isPinging(now)` / `pingProgress(now)`, duración `PING_DURATION_MS`); ambos
presentadores lo disparan tras resolver el salto y ambos renderers lo pintan con un reloj
inyectable (los tests fijan la hora).

- **2D** (`RenderVisitor.paintCursorPing`): un anillo cuadrado de `■` crece una sola vez de radio 1
  a 3 durante 900 ms y se apaga. Cada celda del anillo usa el token `CURSOR_PING` ajustado con
  `TerminalPalette.contrastColorOn` contra el fondo **real** de su celda (mar, montaña, campo,
  también bajo el haz de un faro) y, en ANSI-16, contra su slot, así que nunca se funde con el
  terreno.
- **3D** (`InfrastructureRenderer.visitCursor`): un disco plano emisivo ámbar
  (`cursorPingModel`, constante para que ninguna luz lo apague) se expande bajo el cursor de 1× a
  3.5× y se desvanece con `BlendingAttribute` durante el mismo barrido.

Accesibilidad y coste: es una única pasada suave, sin parpadeo repetido (ni SGR blink, que además
no es fiable en todos los terminales); mientras no hay ping activo no se calcula ni se añade
geometría alguna. El criterio de aceptación es localizarlo en menos de un segundo desde cualquier
zoom y fondo.

Alternativas descartadas:

1. **Parpadeo constante suave** del cursor (dos niveles de brillo): animación permanente, molesta
   y con coste en cada frame.
2. **Ping periódico automático** cada N segundos: mismo problema y difícil de justificar al
   jugador; el criterio pide poder localizarlo *a demanda*.
3. **`SGR.BLINK` de Lanterna**: el emulador Swing lo ignora (ya documentado en el código para la
   carga de vagones) y no permite controlar contraste ni frecuencia.
4. **Invertir solo la celda del cursor**: estático y se confunde con los resaltados de selección;
   el anillo en movimiento destaca mucho más sin parpadear.
5. **Faro/`PointLight` en 3D**: los `PointLight` se retiraron por los artefactos de rejilla del
   suelo (#690); el patrón vigente para luces es geometría emisiva.
6. **Strobe/flash rápido**: descartado por accesibilidad (WCAG 2.3.1).

## Invariantes de la Vista
- Ninguna clase de renderizado debe modificar el estado del `Model`.
- El acceso a los datos de la entidad durante el renderizado debe ser solo de lectura.
- El ciclo de renderizado es independiente del ciclo de simulación (simulación a 20 TPS, renderizado a 60 FPS si el hardware lo permite).
