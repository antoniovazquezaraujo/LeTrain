# Plan de Implementación — Solución a Crash de PulseAudio en Salida

Este plan propone corregir el crash nativo `Assertion 'pthread_mutex_destroy(&m->mutex) == 0' failed` de PulseAudio que ocurre al cerrar LeTrain en Linux.

## Cambios Propuestos

### 1. Detectar el Cierre de la JVM (Shutdown)
El crash ocurre debido a una condición de carrera nativa en OpenJDK/PulseAudio cuando el hilo del mezclador llama a `line.close()` al mismo tiempo que el finalizador nativo del sistema de sonido de Java limpia los recursos durante el apagado de la JVM.
- **Acción**: Agregar un flag estático `shutdownInProgress` en [AudioMixer.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/audio/core/AudioMixer.java) que se active mediante un Shutdown Hook de la JVM.

### 2. Omitir `line.close()` durante el Shutdown
- **Acción**: En el bloque `finally` de `audioLoop()`, comprobar si `shutdownInProgress` es verdadero. Si lo es, omitir la llamada a `line.close()`. De esta forma, el sistema operativo y el propio recolector de basura de la JVM se encargarán de liberar los sockets y descriptores de PulseAudio de manera segura y coordinada, evitando la doble liberación nativa del mutex que causa el abort/aborto del programa.

---

## Plan de Verificación

### Pruebas Automatizadas
- Compilar el proyecto y ejecutar la suite completa de pruebas:
  ```bash
  mvn clean test
  ```
