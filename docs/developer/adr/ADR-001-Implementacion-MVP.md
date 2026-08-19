# ADR-001: Implementación de MVP

## Estado
Propuesto

## Contexto
El simulador necesitaba separar la lógica pesada del simulador ferroviario de la representación visual, permitiendo múltiples interfaces (CLI y GUI 3D).

## Decisión
Se ha implementado el patrón **Model-View-Presenter (MVP)**. 

## Consecuencias
- **Ventajas**: Facilidad para testear la lógica de negocio sin instanciar componentes gráficos complejos.
- **Desventajas**: Mayor cantidad de código "boilerplate" necesario para mantener sincronizados el modelo y el presentador.
