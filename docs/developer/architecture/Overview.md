[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# Arquitectura General (MVP & Visitor)

El proyecto LeTrain utiliza una arquitectura desacoplada para separar la simulación ferroviaria de su representación visual.

## Patrones de Diseño
- **Model-View-Presenter (MVP)**: El `Model` contiene el estado (vías, trenes, economía), la `View` define la interfaz de salida, y el `Presenter` actúa como mediador de eventos y orquestador del loop. Ver [[adr/ADR-001-Implementacion-MVP|ADR-001]].
- **Visitor**: Permite realizar operaciones transversales sobre las entidades. Ver [[ui/RenderingDeepDive|Detalles de Renderizado]].

## Componentes Críticos del Modelo
- **Seguridad**: Gestionada por el [[infrastructure/BlockSystem|Sistema de Colisiones]].
- **Movimiento**: Implementado en la [[vehicles/Physics|Física de Vehículos]].
- **Automatización**: Procesada por el [[systems/CommandPattern|Motor de Automatización (ANTLR)]].

## Símbolos Clave
- `letrain.mvp.impl.Model`: El "hub" central del estado. Gestiona la persistencia con Jackson y Mixins.
- `letrain.mvp.Presenter`: Orquestador del Game Loop y mediador entre Modelo y Vista.
- `letrain.visitor.Visitor`: Interfaz para implementar renderizadores (Gdx3DRenderer, RenderVisitor).
- `letrain.mvp.impl.services.SimulationService`: Servicio que ejecuta la lógica de actualización en cada tick.

## Invariantes del Sistema
- **Persistencia**: El `Model` es serializable a JSON. Los servicios y listeners son `transient` y deben reinyectarse en `postLoadInit()`. Se utiliza `ModelMixin` para separar la lógica de serialización.
- **Desacoplamiento**: La lógica de negocio (`Model`) nunca conoce la implementación de la `View` (GDX o Terminal). Todo intercambio se hace mediante el `Presenter` o `Visitor`.
- **Identificadores Únicos**: El `Model` es la autoridad única para generar y validar IDs de entidades.

## Dependencias
- **Jackson**: Para la persistencia del estado en archivos `.json`.
- **LibGDX**: Para la implementación de la vista 3D.
- **Lanterna**: Para la implementación de la vista en terminal 2D.
