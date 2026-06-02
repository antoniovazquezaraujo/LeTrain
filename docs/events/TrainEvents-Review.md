[ [Índice] ] [[docs/events/TrainEvents|⬅️ Sistema de Eventos]] · [[docs/Index|⬅️ Volver al Índice]]

# Revisión: Sistema de Eventos de Tren

Puntos detectados tras analizar `TrainEventDispatcher`, `Train.java` y los consumidores de eventos. Índice:

1. [[#1 Inconsistencia en null-safety|Inconsistencia en null-safety]]
2. [[#2 Reentrancia parcial|Reentrancia parcial]]
3. [[#3 Exposición de listas internas|Exposición de listas internas]]
4. [[#4 Guard l sensor huérfano|Guard `l != sensor` huérfano]]
5. [[#5 SegmentOccupied sin llamante|`SegmentOccupied` sin llamante]]
6. [[#6 Sin interfaz para el dispatcher|Sin interfaz para el dispatcher]]
7. [[#7 Orden script core no documentado|Orden script/core no documentado]]
8. [[#8 Código repetido en notify methods|Código repetido en notify methods]]

---

## 1. Inconsistencia en null-safety

**Archivo**: `TrainEventDispatcher.java`

**Problema**: Los métodos `notifyXxx` chequean `if (scriptTrainListeners != null)` contra el campo directamente, pero los getters (`getScriptTrainListeners()`) ya hacen lazy init y nunca devuelven null. Tras deserialización, `postLoadInit()` ya garantiza que las listas no sean null.

```java
// notifyXxx - chequea el campo directamente
public void notifySpeedChanged(int speed) {
    if (scriptTrainListeners != null) {          // ← redundante si postLoadInit() funciona
        for (TrainEventListener l : scriptTrainListeners) {
            l.onSpeedChanged(speed);
        }
    }
    if (coreTrainListeners != null) {            // ← redundante
        for (TrainEventListener l : coreTrainListeners) {
            l.onSpeedChanged(speed);
        }
    }
}

// add/remove - usa el getter con lazy init
public void addScriptTrainEventListener(TrainEventListener listener) {
    getScriptTrainListeners().add(listener);     // ← getter nunca devuelve null
}
```

**Posible mejora**: Usar los getters también en los notify (o eliminar los null-checks y confiar en `postLoadInit`).

---

## 2. Reentrancia parcial

**Archivos**: `Train.java` (líneas 226-254), `TrainEventDispatcher.java`

**Problema**: Solo `notifyEnterSensor` y `notifyExitSensor` tienen guarda de reentrancia en `Train.java`. El resto de notificaciones (`link`, `unlink`, `crash`, `contact`, `speedChanged`, `senseChanged`) no.

```java
// Train.java - solo sensor events tienen guarda
public void notifyEnterSensor(Sensor sensor, boolean isForward) {
    if (isNotifying) return;       // ← única guarda
    isNotifying = true;
    try {
        this.eventDispatcher.notifyEnterSensor(sensor, isForward);
    } finally {
        isNotifying = false;
    }
}

// El resto NO tienen guarda
public void notifyLink() {
    this.eventDispatcher.notifyLink();
    // Si un listener de onLink llama a train.notifyLink(), recursión infinita
}
```

**Riesgo**: Si un listener de `onLink` (por ejemplo) vuelve a llamar `train.notifyLink()`, se produce recursión infinita. `CopyOnWriteArrayList` sobrevive, pero el stack se desborda.

**Posible mejora**: Aplicar la misma guarda `isNotifying` a todos los notify, o al menos a los que pueden tener loops (link/unlink/crash).

---

## 3. Exposición de listas internas

**Archivo**: `TrainEventDispatcher.java`

**Problema**: `getScriptTrainListeners()` y `getCoreTrainListeners()` devuelven la lista mutable directamente, permitiendo modificaciones sin pasar por `add/remove`.

```java
// Código actual - expone la lista interna
public List<TrainEventListener> getScriptTrainListeners() {
    if (scriptTrainListeners == null) {
        scriptTrainListeners = new CopyOnWriteArrayList<>();
    }
    return scriptTrainListeners;  // ← cualquiera puede hacer .clear(), .add(), etc.
}
```

**Quién usa estos getters**: `Train.java` los llama. Si algún otro código los usa para modificar la lista, se salta el control de `add/remove`.

**Posible mejora**: Devolver `Collections.unmodifiableList()` para lectura, y forzar el uso de `add/remove` para escritura.

---

## 4. Guard `l != sensor` huérfano

**Archivo**: `TrainEventDispatcher.java`

**Problema**: En `notifyEnterSensor` y `notifyExitSensor` hay una guarda `if (l != sensor)` que evita que el sensor que origina el evento se auto-notifique. Originalmente existía porque `Station` (que extiende `Sensor`) implementaba `TrainEventListener` y se registraba como core listener. Desde que eliminamos eso, ningún objeto `Sensor` está en las listas de listeners, por lo que la guarda nunca se cumple.

```java
public void notifyEnterSensor(Sensor sensor, boolean isForward) {
    scriptTrainListeners.forEach(l -> {
        if (l != sensor) {         // ← nunca es false, ningún Sensor es TrainEventListener
            l.onSensorEnter(train, isForward);
        }
    });
}
```

**Posible mejora**: Eliminar la guarda (simplifica el código). Si en el futuro alguien registra un Sensor como listener, habría que valorar si la auto-exclusión es necesaria.

---

## 5. `SegmentOccupied` sin llamante

**Archivo**: `TrainEventDispatcher.java`

**Problema**: El método `notifySegmentOccupied` está implementado pero no se llama desde ningún sitio.

```java
public void notifySegmentOccupied(Segment segment) {
    if (scriptTrainListeners != null) {
        scriptTrainListeners.forEach(l -> l.onSegmentOccupied(train, segment));
    }
    if (coreTrainListeners != null) {
        coreTrainListeners.forEach(l -> l.onSegmentOccupied(train, segment));
    }
}
```

**Búsqueda**: No hay `train.notifySegmentOccupied()` ni ninguna llamada a este método en toda la base de código.

**Posible mejora**: Eliminar el método (junto con `onSegmentOccupied` de `TrainEventListener`) si no hay planes de usarlo, o implementar el llamante si es necesario.

---

## 6. Sin interfaz para el dispatcher

**Archivo**: `TrainEventDispatcher.java`

**Problema**: El proyecto tiene interfaces para casi todo (por convención en `project-guidelines.instructions.md`), pero `TrainEventDispatcher` es una clase concreta sin interfaz.

```java
// Clase concreta - difícil de mockear
public class TrainEventDispatcher {
    // ...
}
```

**Consecuencias**:
- No se puede mockear en tests unitarios de `Train` o `TrainMovementManager`.
- No se puede reemplazar la implementación sin modificar `Train`.
- `Train` queda acoplado a la implementación concreta.

**Posible mejora**: Extraer interfaz `TrainEventDispatcher` (o renombrar la actual a `TrainEventDispatcherImpl` y crear la interfaz).

---

## 7. Orden script/core no documentado

**Archivo**: `TrainEventDispatcher.java`

**Problema**: Todos los notify recorren primero script y luego core, pero esta precedencia no está documentada ni es configurable.

```java
public void notifyXxx(...) {
    // Siempre: script primero
    if (scriptTrainListeners != null) {
        for (TrainEventListener l : scriptTrainListeners) { ... }
    }
    // Siempre: core después
    if (coreTrainListeners != null) {
        for (TrainEventListener l : coreTrainListeners) { ... }
    }
}
```

**Implicación**: Un listener core que deba ejecutarse *antes* que los scripts (ej: seguridad, logging de auditoría) no tiene forma de hacerlo.

**Posible mejora**: Documentar la precedencia como un contrato, o permitir orden configurable, o separar en interfaces distintas (`ScriptTrainEventListener` y `CoreTrainEventListener`) para evitar ambigüedad.

---

## 8. Código repetido en notify methods

**Archivo**: `TrainEventDispatcher.java`

**Problema**: Los 9 métodos `notifyXxx` siguen exactamente el mismo patrón: recorrer script, recorrer core, con el mismo null-check. Solo cambia el método invocado y sus argumentos.

```java
// 9 variaciones de:
public void notifyXxx(...args...) {
    if (scriptTrainListeners != null) {
        for (TrainEventListener l : scriptTrainListeners) {
            l.onXxx(...args...);
        }
    }
    if (coreTrainListeners != null) {
        for (TrainEventListener l : coreTrainListeners) {
            l.onXxx(...args...);
        }
    }
}
```

**Posible mejora**: Un método auxiliar genérico:

```java
private void notifyAll(Consumer<TrainEventListener> notification) {
    if (scriptTrainListeners != null) {
        scriptTrainListeners.forEach(notification);
    }
    if (coreTrainListeners != null) {
        coreTrainListeners.forEach(notification);
    }
}
```

Y cada notify público sería:

```java
public void notifySpeedChanged(int speed) {
    notifyAll(l -> l.onSpeedChanged(speed));
}
```

Esto elimina la repetición, centraliza el null-check y la decisión del orden script/core en un solo sitio.

---

## Resumen

| # | Asunto | Impacto | Esfuerzo |
|---|---|---|---|
| 1 | Null-safety inconsistente | Bajo (defensivo, no bug) | Trivial |
| 2 | Reentrancia parcial | Medio (posible stack overflow) | Bajo |
| 3 | Listas expuestas | Bajo (encapsulación) | Trivial |
| 4 | Guard `l != sensor` huérfano | Bajo (código muerto) | Trivial |
| 5 | `SegmentOccupied` sin llamante | Bajo (código muerto) | Trivial |
| 6 | Sin interfaz para dispatcher | Medio (testabilidad) | Medio |
| 7 | Orden script/core no documentado | Bajo (diseño) | Bajo |
| 8 | Código repetido | Bajo (mantenibilidad) | Bajo |

**Prioridad sugerida**: 2 → 3 → 4+5+8 → 1 → 6 → 7
