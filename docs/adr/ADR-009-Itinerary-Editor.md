# ADR-009: Editor de Itinerarios (Diseño UI)

## Estado: PROPUESTA / REFERENCIA

> Documento recuperado del diseño original. Sirve como referencia para la Fase 7 de ADR-008.

---

# EDITOR DE ITINERARIOS
- Un itinerario es una lista ordenada de estaciones, cada una con una acción a realizar. 
- Cada estación, si no tiene nombre aparecerá como "Estación n" siendo n su id
- Cada itinerario, si no tiene nombre se llamará "a -> b", con el nombre de la primera estación como a y el de la última como b. **El nombre se regenera dinámicamente** — solo se usa como fallback cuando no se ha dado un nombre explícito.
- Se mostrará una lista de itinerarios con dos campos: el nombre del itinerario y el id del posible tren al que se asigna y un id del tren al que se ha asignado
- Mostrará la lista de itinerarios, cada uno con su nombre y el id del tren al que se ha asignado
- Permitirá agregar, eliminar o editar cada itinerario
- Permitirá asignarle o quitarle un tren, a elegir entre los que hay
- Si una estación no se puede alcanzar, siguiendo el itinerario desde la primera estación, no se podrá agregar
- **Un tren solo puede tener asignado UN itinerario a la vez** (relación 1:1)
- **Un itinerario no se puede guardar hasta que tenga al menos 2 estaciones**
- **Cada vez que se cambia algo del mapa, hay que llamar a `model.analyzeInfrastructure()`** para que se rediscoveran los cantones y se revaliden las rutas
 

## Ventana Principal: Lista de Itinerarios
**Propósito**: Listar itinerarios y gestionar asignaciones de tren.

```
┌─────────────────────────────────────────┐
│            Itinerarios                  │
├─────────────────────────────────────────┤
│ Ruta                │ Tren              │
├─────────────────────────────────────────┤
│ Paris → Barcelona   │ [Train 2    ▼]    │
│ Vigo → Ourense      │ [           ▼]    │
│ Madrid → Toledo     │ [Train 1    ▼]    │
├─────────────────────────────────────────┤
│[Agregar]    [Eliminar]                  │
│                                         │
│                     [Aceptar][Cancelar] │
└─────────────────────────────────────────┘

```

**Detalles**:
- **Encabezados**: Dos columnas con "Ruta" y "Tren"
- **Dropdown de tren**: Muestra trenes disponibles + opción vacía para desasignar
- **`[ ]` (vacío)**: Indica itinerario sin tren asignado
- **Botón `[Agregar]`**: Crea nuevo itinerario (abre ventana de detalle)
- **Botón `[Eliminar]`**: Elimina el itinerario seleccionado
- **Doble clic o Enter** en fila: Abre ventana de detalle para editar estaciones
- **Tecla Suprimir**: Elimina el itinerario seleccionado
- Validación de relaciones 1:1: solo se permiten asignar trenes que no tengan ya un itinerario asignado

## Ventana de Detalle: Editor de Estaciones
**Propósito**: Editar las estaciones y sus acciones. Los trenes se gestionan en la ventana principal.

```
┌─────────────────────────────────────────┐
│ Itinerario [Madrid → Salamanca ]        │
├─────────────────────────────────────────┤
│ Estaciones:                             │
│ ┌─────────────────────────────────────┐ │
│ │ Madrid         │ [PASO      ▼]      │ │
│ │ Ciudad Real    │ [PARADA    ▼]      │ │
│ │ Zamora         │ [CARGA     ▼]      │ │
│ │ Salamanca      │ [DESCARGA  ▼]      │ │
│ └─────────────────────────────────────┘ │
│ [Agregar]  [Eliminar]                   │
│                      [Aceptar][Cancelar]│
└─────────────────────────────────────────┘
```

**Detalles**:
- **Campo nombre**: Editable, con valor por defecto generado automáticamente ("origen → destino")
- **Lista de estaciones**: 
  - Columna izquierda: Nombre de la estación (no editable). Si no tiene nombre, se muestra "Estación n"
  - Columna derecha: Dropdown con acciones disponibles [PASO | PARADA | CARGA | DESCARGA]
- **Botón `[Agregar]`**: Abre selector de estaciones alcanzables desde la última estación de la lista. Solo se pueden agregar estaciones que tengan ruta desde la última estación.
- **Botón `[Eliminar]`**: Elimina la estación seleccionada
- **Tecla Suprimir**: También elimina la estación seleccionada
- **Validación**: El itinerario debe tener al menos 2 estaciones para poder guardar
- **NO hay campo de tren**: La asignación se hace en la ventana principal

## Reglas de Validación
- **Nombre dinámico**: Si no se especifica un nombre, se genera como "origen → destino". El nombre se regenera automáticamente si cambian las estaciones.
- **Estaciones alcanzables**: Solo se pueden agregar estaciones que tengan ruta desde la última estación del itinerario
- **Mínimo de estaciones**: Un itinerario no se puede guardar con menos de 2 estaciones
- **Relación 1:1 tren-itinerario**: Un tren solo puede tener un itinerario asignado, y un itinerario solo puede tener un tren asignado
- **Validación de infraestructura**: Cada vez que se modifica el mapa, debe llamarse a `model.analyzeInfrastructure()` para redescubrir cantones y revalidar rutas

