# Arquitectura de LeTrain

Este documento describe la arquitectura interna, los sistemas principales y los flujos de datos de LeTrain. Está pensado para ser actualizado a medida que el sistema evoluciona.

## 1. Patrón Arquitectónico: MVP (Model-View-Presenter)

LeTrain utiliza el patrón **MVP** para separar la lógica de negocio de la interfaz de usuario:

- **Model (`letrain.mvp.Model`)**: Contiene el estado completo del juego (vías, trenes, estaciones, economía). Es el "Single Source of Truth". Proporciona métodos para manipular este estado.
- **View (`letrain.mvp.View`)**: Define la interfaz de lo que el usuario ve y cómo interactúa. Hay dos implementaciones principales:
    - `TerminalView`: Implementación 2D para consola usando la librería Lanterna.
    - `Gdx3DView`: Implementación 3D usando la librería LibGDX.
- **Presenter (`letrain.mvp.Presenter`)**: Actúa como mediador. Recibe eventos de la View (teclado, ratón) y actualiza el Model. También coordina la actualización de la View cuando el Model cambia.

## 2. Sistema de Renderizado: Patrón Visitor

Para desacoplar las entidades del juego de su representación visual, se utiliza el **patrón Visitor**:

- Cada objeto del mapa (`RailTrack`, `Locomotive`, `Wagon`, `Station`, etc.) implementa la interfaz `Renderable`, que tiene un método `accept(Visitor v)`.
- El `Visitor` (como `RenderVisitor` para 2D o `Gdx3DRenderer` para 3D) sabe cómo dibujar cada tipo de objeto.
- **Vista 3D (GDX)**: Utiliza sub-renderizadores especializados (`TrackRenderer`, `VehicleRenderer`, `InfrastructureRenderer`, `GroundRenderer`) para organizar la complejidad del mundo 3D.

## 3. Infraestructura Ferroviaria: Tracks y Cantones

### Mapa y Rutas
- **`RailMap`**: Una estructura de datos que organiza todos los `RailTrack` en el espacio (X, Y).
- **`Track` / `RailTrack`**: Representa un tramo de vía. Utiliza un `Router` para definir por qué direcciones se puede entrar y salir.
- **`ForkRailTrack`**: Un desvío ferroviario que puede alternar entre una ruta normal y una alternativa.

### Seguridad y Colisiones
- El sistema utiliza una lógica de **chequeo de ocupación baldosa a baldosa** en `Train#moveLinkers`.
- Antes de avanzar, cada pieza del tren verifica si la siguiente baldosa está libre.
- **Colisión (`crash`)**: Si se detecta ocupación por otro tren a velocidad $\ge 5$.
- **Parada de Seguridad**: Si la velocidad es $< 5$, el tren se detiene inmediatamente.

## 4. Vehículos y Trenes

### Composición del Tren
- **`Train`**: Es el contenedor de un convoy. Implementa `Trailer` y gestiona una cola de `Linker`s.
- **`Linker`**: Clase base para cualquier elemento enganchable.
    - **`Locomotive`**: El "Director" del tren. Implementa `Tractor`, gestiona la velocidad, inercia y dirección.
    - **`Wagon`**: Gestiona carga, descarga y tipos de mercancía (`CargoTypes`).

### Movimiento y Colisiones
El movimiento de los trenes se realiza en **dos pasos** para garantizar la seguridad:
1. **Validación**: Se comprueba si todos los vagones del tren pueden avanzar a sus siguientes posiciones (vías conectadas, sin colisiones con otros trenes, cantones permitidos).
2. **Ejecución**: Si la validación es positiva, se actualizan las posiciones físicas y los estados de los sensores/semáforos.

## 5. Automatización

### Programas ANTLR
- LeTrain permite la automatización mediante scripts basados en la gramática `LeTrainProgram.g4`.
- Los scripts permiten reaccionar a eventos de sensores y estaciones para controlar semáforos, desvíos y el comportamiento de los trenes.
- El `AutomationEngine` gestiona el parseo y la instalación de listeners en los objetos del modelo.

## 6. Audio y Síntesis
LeTrain incluye un motor de audio avanzado que sintetiza sonidos de motores diesel, frenos y ambiente basándose en el estado físico del tren (velocidad, revoluciones del motor, posición).
