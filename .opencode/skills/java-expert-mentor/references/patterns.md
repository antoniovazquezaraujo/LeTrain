# Patrones de Diseño y Arquitectura en LeTrain

## 1. Principios SOLID
- **Single Responsibility (SRP)**: Cada clase (ej. `BlockManager`, `AudioController`) debe tener una sola razón para cambiar.
- **Open/Closed (OCP)**: Las entidades deben estar abiertas a extensión pero cerradas a modificación. Usar interfaces para añadir nuevos comportamientos (ej. nuevos tipos de `Trackeable`).
- **Liskov Substitution (LSP)**: Cualquier subclase (ej. `ForkRouter`) debe ser sustituible por su clase base (`Router`) sin romper el sistema.
- **Interface Segregation (ISP)**: Interfaces pequeñas y específicas. En lugar de una interfaz gigante `Map`, tenemos `Mapeable`, `Reversible`, `Rotable`.
- **Dependency Inversion (DIP)**: Depender de abstracciones, no de implementaciones. (Inyección de dependencias).

## 2. Patrones de Comportamiento Aplicados
### Patrón Visitor
Utilizado en el paquete `letrain.visitor`. 
- **Propósito**: Separar el algoritmo de la estructura de datos (ej. renderizado de objetos en el mapa sin que la clase `Track` tenga que saber cómo dibujarse).
- **Cuándo aplicar**: Cuando hay un conjunto estable de clases pero operaciones cambiantes sobre ellas.

## 3. Patrones Creacionales
### Factory Method
Utilizado en el sistema de `Station` y `BlockSection`.
- **Propósito**: Encapsular la creación de objetos complejos. Útil para la generación procedural del mundo, donde la configuración del terreno puede variar.

## 4. Estrategia de Abstracción
- **Evitar Abstracción Prematura**: No crear una interfaz a menos que haya al menos dos implementaciones distintas o un caso de uso claro para pruebas unitarias.
- **Desacoplamiento**: El paquete `mvp` (Model-View-Presenter) es clave. Mantener la lógica de negocio en `Model` y la presentación (3D o Terminal) en `View` comunicadas vía `Presenter`.
