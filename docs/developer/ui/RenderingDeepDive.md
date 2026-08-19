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

## Invariantes de la Vista
- Ninguna clase de renderizado debe modificar el estado del `Model`.
- El acceso a los datos de la entidad durante el renderizado debe ser solo de lectura.
- El ciclo de renderizado es independiente del ciclo de simulación (simulación a 20 TPS, renderizado a 60 FPS si el hardware lo permite).
