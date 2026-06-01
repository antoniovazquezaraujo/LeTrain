# Plan de Implementación — Solución a Entrecortamiento y Silenciado del Audio

Este plan propone corregir los problemas de entrecortamiento (crackle) y silenciado completo del audio en LeTrain mediante la ampliación del buffer de la línea de sonido y aislando la lectura de cada fuente de audio individual para evitar bucles de bloqueo.

## Cambios Propuestos

### 1. Incrementar el Buffer de la Línea de Audio
En [AudioMixer.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/audio/core/AudioMixer.java), la línea se abre actualmente con:
```java
line.open(format, BUFFER_SIZE * 4); // 4096 frames
```
Sin embargo, `BUFFER_SIZE * 4` (4096 bytes) representa solo 1024 frames (23ms de audio a 16-bit estéreo). Esto no deja ningún margen de amortiguación (cushion) frente a pequeñas demoras de la JVM.
- **Acción**: Cambiar la apertura del buffer a `BUFFER_SIZE * 16` (16384 bytes, equivalentes a 4096 frames o ~93ms), lo que coincide con el comentario del código y proporciona un margen de seguridad robusto contra el entrecortamiento.

### 2. Aislar la Lectura de Fuentes Individuales
Actualmente, si una fuente de audio lanza una excepción durante `source.read(sourceBuffer)`, el bucle principal de mezcla la captura en el bloque `catch` externo de `audioLoop()`, forzando un retardo de `10ms` y saltando la escritura a la línea de audio. Como la fuente errónea nunca se elimina, el mezclador entra en un bucle infinito de excepciones y retardos de 10ms, lo que produce un entrecortamiento severo y el silenciado definitivo de todo el audio.
- **Acción**: Envolver la lectura y mezcla de cada `AudioSource` en un bloque `try-catch` propio. Si una fuente lanza una excepción, se registra el error y se remueve esa fuente del mezclador, permitiendo que el resto del audio continúe reproduciéndose con normalidad sin interrumpir el bucle de mezcla y reproducción.

---

## Plan de Verificación

### Pruebas Automatizadas
- Compilar el proyecto y ejecutar la suite completa de pruebas:
  ```bash
  mvn clean test
  ```
