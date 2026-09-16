# 🚂 Wiki de LeTrain - Índice Principal

Bienvenido a la documentación técnica de **LeTrain**. Esta wiki está diseñada para ser navegada en Obsidian, aprovechando los enlaces bidireccionales entre sistemas.

## 🏗️ Arquitectura y Diseño
- [[../PHILOSOPHY|Manifiesto y Filosofía de Diseño]] ([English](../PHILOSOPHY.md) | [Español](../PHILOSOPHY_es.md))
- [[architecture/Overview|Visión General del Sistema (MVP & Visitor)]]
- [[architecture/GameLoop|Bucle Principal del Juego (Game Loop: 2D vs 3D)]]
- [[architecture/VehicleMovement|Bucle de Movimiento de Vehículos y Pipeline]]
- [[architecture/AutoPilotAnalysis|Análisis del Sistema AutoPilot]]
- [[architecture/SafetyManagerAnalysis|Análisis de TrainSafetyManager]]
- [[architecture/PresenterSymmetryProposal|Propuesta de Simetría en Presentadores y Vistas]]
- [[architecture/TrainMovementManagerStudy|Estudio de Relación Train y TrainMovementManager]]
- [[architecture/ClassIndex|Índice de Clases (generado automáticamente)]]

## 🛤️ Infraestructura Ferroviaria
- [[infrastructure/TrackTypes|Jerarquía de Vías (Forks, Puentes, Túneles, TrackComponent)]]
- [[infrastructure/BlockSystem|Seguridad y Colisiones (Segment Blocking)]]
- [[infrastructure/safety_locks_analysis|Análisis de Bloqueos Iniciales (acquireInitialLocks)]]
- [[infrastructure/siding_bypass_analysis|Análisis de Apartaderos (Siding Bypass & Wakeup)]]
- [[infrastructure/Informe_Bloqueos|Informe de Simplificación de Bloqueos]]

## 🚆 Material Rodante (Vehículos)
- [[vehicles/Physics|Física de Movimiento, Inercia, Curvas y Colisiones]]
- [[vehicles/CargoSystems|Sistema de Carga y Economía]]

## ⚡ Sistema de Eventos
- [[events/TrainEvents|Eventos de Tren y TrainEventDispatcher]]
- [[events/TrainEvents-Review|Revisión del Sistema de Eventos]]

## 🧭 Navegación, Automatización y DSL
- [[navigation/AStarPathfinder|Navegación Autónoma (A* y AutoPilot)]]
- [[systems/CommandPattern|Gestión de Automatización (ANTLR4)]]
- [[systems/ActionCatalogue|Action Catalogue — Acciones de edición vs. comandos]]
- [[systems/PassengerTrains_Design|Diseño de Trenes de Pasajeros (Propuesta)]]

## 🎨 Interfaz y Visualización
- [[ui/RenderingDeepDive|Profundización en el Motor de Renderizado (Visitor 2D y 3D)]]

## 📜 Registro de Decisiones de Arquitectura (ADRs)
- [[adr/ADR-000-Design-decisions|ADR-000: Sistema de Guiado Automático (Fase 1: Guiado Topológico)]]
- [[adr/ADR-001-Implementacion-MVP|ADR-001: Implementación del Patrón MVP]]
- [[adr/ADR-002-Block-System|ADR-002: Sistema de Bloqueo por Puertos]]
- [[adr/ADR-003-Segments|ADR-003: Topología del Grafo y Segmentos]]
- [[adr/ADR-004-Itinerary|ADR-004: Gestión de Itinerarios]]
- [[adr/ADR-005-Block-Segments|ADR-005: Sistema de Seguridad por Segmentos Atómicos]]
- [[adr/ADR-006-Symmetry-in-Presenters|ADR-006: Simetría en Presentadores y Vistas]]
- [[adr/ADR-007-Collision-Visual-Interpolation|ADR-007: Interpolación Visual en Colisiones]]
- [[adr/ADR-008-Itinerary-Redesigned|ADR-008: Rediseño de Itinerarios]]
- [[adr/ADR-009-Itinerary-Editor|ADR-009: Editor de Itinerarios (DSL)]]
- [[adr/ADR-010-Test-Plan-AutoPilot|ADR-010: Plan de Tests del AutoPilot]]
- *(ADR-011: No asignado en la serie histórica)*
- [[adr/ADR-012-AutoPilot-Simplification|ADR-012: Simplificación del Sistema AutoPilot]]
- [[adr/ADR-013-Nuevas-Ideas|ADR-013: Notas y Nuevas Ideas]]
- [[adr/ADR-014-SafetyManager-Autopilot-Decoupling|ADR-014: Desacoplamiento de SafetyManager y AutoPilot]]
- [[adr/ADR-015-Abstraccion-Puertos-Nodos|ADR-015: Abstracción de Puertos y Nodos]]
- [[adr/ADR-016-Command-Line-Interface|ADR-016: Interfaz de Línea de Comandos (CLI)]]
- [[adr/ADR-017-Track-Elements-Refactoring|ADR-017: Refactorización de Elementos de Vía (TrackComponent)]]
- [[adr/ADR-018-2D-Entities-Rendering|ADR-018: Renderizado de Entidades 2D]]
- [[adr/ADR-019-Derailment-Curves-Speed|ADR-019: Descarrilamiento por Velocidad en Curvas]]
- [[adr/ADR-020-Command-Journal-Scenarios|ADR-020: Diario de Comandos y Escenarios]] ([[adr/ADR-020-Guia-Practica|Guía Práctica]])
- [[adr/ADR-021-Model-Decomposition-Strategy|ADR-021: Estrategia de Descomposición de Model.java]]
- [[adr/ADR-022-Game-Time|ADR-022: Tiempo de Juego (Reloj, Día/Noche y Horarios)]]

## 📦 Release y Distribución
- [[release/Release_Process|Proceso de Release y Despliegue]]
- [[release/itch-description|Store Description (Itch.io)]]

---
*Nota: Esta wiki se mantiene actualizada periódicamente para reflejar el estado real del repositorio.*
