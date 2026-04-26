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

## Invariantes de la Vista
- Ninguna clase de renderizado debe modificar el estado del `Model`.
- El acceso a los datos de la entidad durante el renderizado debe ser solo de lectura.
- El ciclo de renderizado es independiente del ciclo de simulación (simulación a 10 Hz, renderizado a 60 FPS si el hardware lo permite).
