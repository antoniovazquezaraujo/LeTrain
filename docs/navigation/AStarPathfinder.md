[[Index|⬅️ Volver al Índice]]

# Navegación Autónoma (Funcionalidad Planificada)

> **NOTA:** El sistema de navegación autónoma basado en el algoritmo A* está actualmente en fase de diseño y no ha sido implementado en la rama principal del proyecto.

## Visión General
El objetivo de este sistema es permitir que los trenes encuentren la ruta más corta entre dos estaciones de forma automática, gestionando los desvíos (`Forks`) sin intervención del usuario.

## Componentes Previstos
- **`AStarPathfinder`**: Clase encargada de encontrar la ruta más corta entre dos puntos del `RailMap`.
- **`Itinerary`**: Estructura de datos que almacenará la secuencia de estaciones y acciones (Cargar/Descargar) de un tren.
- **Integración con Automatización**: Se planea añadir comandos como `GO TO STATION id` a la gramática de automatización ANTLR.

## Estado Actual
Actualmente, el movimiento autónomo se gestiona mediante eventos de sensores y semáforos definidos en programas de automatización. El tren simplemente avanza en su dirección actual y el usuario (o el programa) debe asegurarse de que los desvíos estén correctamente posicionados.
