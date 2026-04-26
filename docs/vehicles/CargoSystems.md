# Sistema de Carga y Economía

LeTrain implementa un sistema de gestión de mercancías basado en tipos de carga, vagones especializados y un motor económico configurable.

## Gestión de Mercancías
- **`letrain.track.CargoTypes`**: Enum centralizado que define todas las mercancías del juego (e.g., GOLD, COAL, RUBY, WATER, ROCK).
- Las mercancías se gestionan como cantidades enteras dentro de los vagones y estaciones.

## Interfaz de Vagones
La capacidad de transportar carga se define en la clase `Wagon`:
- **`letrain.vehicle.impl.rail.Wagon`**: Implementa métodos como `loadCargo(amount)`, `takeCargo(amount)`, `getMaxCapacity()` y `getCargoAmount()`.
- Los vagones se especializan en ciertos tipos de carga mediante el campo `cargoType`.

## Lógica de Intercambio (Estaciones)
El método crítico para la interacción entre el tren y la infraestructura es `Train#performIndustrialAction(Station)`:
1. El tren llega a una estación que ofrece o demanda ciertos tipos de carga.
2. `performIndustrialAction()` recorre los vagones del tren (`Linkers`).
3. Para cada vagón que sea un `Wagon`, se intenta cargar o descargar basándose en la disponibilidad de la estación.
4. La velocidad de transferencia depende del `transferRate` de la estación (basado en la densidad industrial circundante).

## Configuración Económica
El sistema carga sus parámetros desde el archivo `economy.properties`:
- **Precios Base**: Define el valor de cada acción económica (CONSTRUCTED_STATION, LOAD_PASSENGERS, etc.).
- **Umbrales de Terreno**: Determinan dónde se generan las minas e industrias en el `GroundMap`.

## Símbolos Clave para Desarrolladores
- `CargoTypes.IndustryMapper`: Utilidad para mapear tipos de terreno a mercancías y roles de estación.
- `Station#getStorage()`: Accede al inventario de la estación.
- `Wagon#getCargoAmount()`: Devuelve la cantidad de carga actual.
