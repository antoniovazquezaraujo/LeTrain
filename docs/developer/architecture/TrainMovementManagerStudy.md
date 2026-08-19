# Estudio de la Relación entre Train y TrainMovementManager

Este documento analiza la división de responsabilidades actual entre la clase `Train` (el orquestador central) y `TrainMovementManager` (el gestor físico de movimiento), con el objetivo de encapsular por completo la lógica de movimiento dentro de este último y simplificar la clase de entidad.

---

## 1. Estado Actual de Responsabilidades

### A. Responsabilidades en `Train.java` (Relacionadas con Movimiento)
Aunque existe `TrainMovementManager`, la clase `Train` retiene varias responsabilidades físicas críticas y lógicas previas y posteriores al desplazamiento de los vagones:

1. **Orquestación y Pre-verificaciones de Estado (`advance()`)**:
   - Comprobación de si el tren está cargando mercancías (`isLoading()`).
   - Comprobación de si el tren está descarrilado/detenido por colisión (`isStalled()`).
   - Verificación de permisos de seguridad por ocupación de cantones (`hasPermissionToMove()`).
   - Forzar la detención de locomotoras (`setTargetSpeed(0)`) si alguna comprobación falla.
2. **Cálculo y Sincronización de Direcciones Físicas**:
   - `setDirPushedLinkers(boolean)`: Corrige la orientación del enlace para vagones empujados.
   - `setDirTowedLinkers(boolean)`: Corrige la orientación del enlace para vagones remolcados.
   - `refreshLinkersDirection()`: Refresca el sentido físico de todos los acoplamientos del tren.
3. **Backup y Restauración de Sentido en Fallo de Avance**:
   - Almacenamiento temporal de las direcciones (`Dir` y `EntryDir`) de todos los linkers en `advance()` antes de moverlos.
   - Restauración manual de las direcciones de los linkers si el movimiento resulta fallido o bloqueado, para evitar distorsiones visuales en el renderizador.
4. **Métodos Físicos de Velocidad**:
   - `initiateBraking()` y `restoreSpeed(int)`.

### B. Responsabilidades en `TrainMovementManager.java` (Implementación)
El gestor realiza actualmente el trabajo de bajo nivel para el desplazamiento físico:
1. **Algoritmo de Movimiento en Dos Pasos (`moveLinkers()`)**:
   - **Paso 1 (Validación)**: Valida si cada linker puede entrar a la siguiente celda y calcula reservas. Detecta colisiones físicas de tren contra tren.
   - **Paso 2 (Ejecución)**: Desplaza físicamente los vehículos, dispara eventos de entrada/salida en sensores, semáforos, agujas/forks y gestiona la reversión de transacciones (rollback).
2. **Detección y Gestión de Colisiones / Descarrilamientos (`crash()`)**:
   - Ejecuta la destrucción física de los linkers del tren y la notificación sonora/acústica del impacto.
   - Libera todos los cantones bloqueados en el `BlockManager`.
3. **Corrección de Dirección Post-Impacto (`correctDirection()`)**:
   - Corrige la dirección de un linker para asegurar que se acopla a las vías físicas disponibles.

---

## 2. Puntos de Acoplamiento y Problemas Detectados

- **Acoplamiento Bidireccional Estricto**: `TrainMovementManager` mantiene una referencia directa a la instancia concreta de `Train` y hace uso de sus propiedades internas (como `autoMode`, `id`, `getLinkers()`, `getSpeed()`, `setStalled()`, etc.).
- **Lógica de Avance Fragmentada**: La lógica que decide si un tren se desplaza está en `Train.java` (`advance()`), mientras que la lógica que realiza el movimiento y gestiona colisiones está en `TrainMovementManager.java` (`moveLinkers()`). Si ocurre una colisión, se modifica el estado `stalled` en ambos sitios y se restauran las direcciones en `Train.java` a partir de un valor de retorno booleano de `moveLinkers()`.
- **Modificación Física y Visual de Linkers**: La lógica que calcula cómo deben curvarse visualmente los vagones remolcados (`setDirTowedLinkers`) o empujados (`setDirPushedLinkers`) sigue residiendo en la entidad `Train.java`, a pesar de ser puramente de tracción física y geométrica.

---

## 3. Propuesta de Refactorización

Para encapsular todo lo relativo al movimiento dentro de `TrainMovementManager`, proponemos el siguiente plan de diseño:

### Paso 1: Mover la Lógica de Dirección de Linkers a `TrainMovementManager`
Trasladar las funciones de cálculo de orientación física del tren desde `Train.java` a `TrainMovementManagerImpl`:
- `setDirPushedLinkers(boolean)`
- `setDirTowedLinkers(boolean)`
- `refreshLinkersDirection()`

Para ello, la interfaz de `TrainMovementManager` expondrá:
```java
void refreshLinkersDirection();
```

### Paso 2: Trasladar `advance()` al Manager
Migrar el método `advance()` al manager, de modo que `Train` únicamente delegue la acción:

#### En `Train.java`:
```java
public boolean advance() {
    return movementManager.advance();
}
```

#### En `TrainMovementManager.java` (Interfaz):
```java
boolean advance();
```

#### En `TrainMovementManager.java` (Implementación):
```java
@Override
public boolean advance() {
    if (train.isLoading()) {
        log.info("Train {} advance: cannot move because train is loading", train.getId());
        return false;
    }

    if (train.isStalled()) {
        log.info("Train {} advance: cannot move because train is stalled", train.getId());
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            head.setTargetSpeed(0);
        }
        return false;
    }

    if (train.getModel() != null) {
        if (!train.hasPermissionToMove()) {
            log.info("Train {} advance: cannot move because hasPermissionToMove is false. Forcing setTargetSpeed(0)", train.getId());
            Tractor head = train.getDirectorLinker();
            if (head != null) {
                head.setTargetSpeed(0);
            }
            return false;
        }
    }

    log.info("Train {} advance: proceeding to moveLinkers", train.getId());

    boolean normalSense = true;
    Tractor head = train.getDirectorLinker();
    if (head != null && head.isReversed()) {
        normalSense = false;
    }

    // Backup de direcciones
    Map<Linker, Dir> savedDirs = new HashMap<>();
    Map<Linker, Dir> savedEntryDirs = new HashMap<>();
    for (Linker l : train.getLinkers()) {
        savedDirs.put(l, l.getDir());
        savedEntryDirs.put(l, l.getEntryDir());
    }

    refreshLinkersDirection(normalSense); // Método interno o helper
    boolean moved = moveLinkers(normalSense);

    if (!moved || train.isStalled()) {
        Linker first = train.getLinkers().isEmpty() ? null : train.getLinkers().getFirst();
        for (Linker l : train.getLinkers()) {
            if (train.isStalled() && l == first) {
                continue;
            }
            Dir savedDir = savedDirs.get(l);
            Dir savedEntry = savedEntryDirs.get(l);
            if (savedDir != null) {
                l.setDir(savedDir);
            }
            if (savedEntry != null) {
                l.setEntryDir(savedEntry);
            }
        }
    }

    return moved;
}
```

### Paso 3: Simplificar la API pública de `Train`
Al mover este bloque al manager, podemos reducir los wrappers en `Train.java` y mantener la clase limpia:
- Eliminar de `Train.java` los métodos privados de orientación.
- Delegar directamente en el manager cualquier refresco de orientación externo.

---

## 4. Beneficios del Diseño Propuesto

1. **Bajo Acoplamiento y Cohesión**: La clase `Train` se reduce en ~150 líneas de código y se mantiene enfocada en persistencia, acoplamientos lógicos de vagones e itinerarios de alto nivel.
2. **Encapsulamiento Físico**: Toda la matemática del avance físico y actualización visual de curvas se consolida en un único lugar (`TrainMovementManager`), facilitando mejoras o pruebas unitarias del algoritmo de tracción.
3. **Mantenibilidad**: Los bugs relacionados con desalineamiento visual de vagones al descarrilar o frenar se aíslan al ámbito de responsabilidad del gestor de movimiento.
