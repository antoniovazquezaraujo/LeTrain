[ [Índice] ] [[docs/Index|⬅️ Volver al Índice]]

# ADR-004: Gestión de Itinerarios y Control Automático (Propuesta)

## Estado: PROPUESTA / EN DISEÑO

## Contexto
Tras el descarte de implementaciones anteriores que resultaron inestables, se propone un nuevo modelo de conducción automática basado en la infraestructura de `RailwayGraph` (ADR-003). La meta es crear un conductor virtual (`AutoPilot`) que sea robusto y determinista.

## Objetivos del Diseño
1.  **Conducción Basada en Grafo**: El sistema de navegación debe usar los `Segments` y `PathSteps` del grafo topológico, no coordenadas físicas.
2.  **Seguridad por Diseño**: El tren solo puede avanzar si tiene garantizada la reserva del siguiente segmento (vía ADR-005).
3.  **Determinismo**: El jugador debe poder activar el modo automático solo si existe una ruta válida desde la posición y orientación actual.

## Entidades Propuestas

### 1. El Itinerario (Itinerary)
Una secuencia lógica de estaciones u objetivos. A diferencia de las versiones descartadas, el itinerario no debe contener la ruta física, sino solo los "puntos de paso". La ruta física se recalcula dinámicamente según el estado de los desvíos y la ocupación.

### 2. El Piloto Automático (AutoPilot)
Componente que se acoplará al tren para:
- Solicitar rutas al `PathResolver`.
- Negociar bloqueos con el `BlockManager`.
- Ajustar la velocidad física de la locomotora.
- Reaccionar a eventos de estación (Carga/Descarga).

## Reglas de Navegación Propuestas
- **Engagement**: El piloto automático solo se activa si la locomotora "ve" un camino claro hacia el primer punto del itinerario.
- **Inversión Atómica**: Al llegar al final de una línea, el tren debe realizar una secuencia segura: Parada completa -> Inversión de marcha -> Recálculo de ruta.
- **Prioridad de Seguridad**: Ante cualquier pérdida de integridad del bloque o ruta, el `AutoPilot` debe devolver el control al manual tras una frenada de emergencia.

## Próximos Pasos
1.  Definir la interfaz `AutoPilot` y su integración con `Locomotive`.
2.  Implementar el buscador de rutas `A*` que trabaje sobre los nuevos `Segments`.

---
*Última actualización: 2026-04-28*
