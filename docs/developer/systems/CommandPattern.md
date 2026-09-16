[[Index|⬅️ Volver al Índice]]

# Gestión de Automatización (ANTLR)

LeTrain utiliza un sistema de programas de automatización para gestionar la lógica de circulación, permitiendo que el mundo reaccione a eventos de trenes (sensores, estaciones, etc.) de forma programable.

## Flujo de Automatización
1. **Entrada de Programa**: El usuario escribe un script de automatización en la interfaz de "Programación".
2. **Procesamiento (ANTLR4)**: La clase `letrain.mvp.impl.services.AutomationEngine` utiliza gramáticas formales ANTLR4 (`ScriptLogicParser.g4`, `PlayerCommandsParser.g4` y `LeTrainLexer.g4` en `core/src/main/antlr4/letrain/command/`) para parsear los programas y comandos del usuario.
3. **Instalación de Listeners**: El `CommandManager` recorre el árbol sintáctico y añade `EventListeners` a los objetos del mapa (sensores, estaciones, desvíos).
4. **Ejecución**: Cuando un tren dispara un evento (e.g., entra en un sensor), se ejecutan los comandos asociados al bloque correspondiente.

## Ejemplo de Script
```letrain
sensor 1 on train enter {
    semaphore 5 set closed;
    fork 3 set curved;
}

station 10 on train couple {
    train accelerate;
}
```

## Tipos de Acciones
- **Semáforos**: `set open | closed | invert`.
- **Desvíos (Forks)**: `set straight | curved | flip`.
- **Trenes**: `accelerate | decelerate | set speed <n> | invert | load | unload | set engine on/off`.
- **Enganches**: `couple | uncouple`.

## Símbolos Clave
- `letrain.mvp.impl.services.AutomationEngine`: Punto de entrada para el parseo de programas.
- `letrain.command.CommandManager`: Implementa el patrón Visitor de ANTLR para traducir el script en lógica ejecutable.
- `letrain.track.SensorEventListener`: Interfaz utilizada para reaccionar a los eventos del mundo físico.

## Invariantes
- Los programas de automatización se limpian y reinstalan completamente cada vez que se aplica un nuevo script.
- Las acciones de los comandos se ejecutan en el contexto del tren que disparó el evento (si aplica) o sobre el objeto específico referenciado por su ID.
