# ADR-012: Simplificación del Sistema AutoPilot

## Estado: APROBADO

## Contexto

El diseño anterior del AutoPilot (ADR-008) asumía la responsabilidad de controlar la velocidad del tren, realizar paradas en estaciones, esperar ticks de tiempo, invertir el sentido de la marcha (`REVERSE`), e iniciar los procesos de carga y descarga de mercancías (`LOAD`/`UNLOAD`).

Esta acumulación de responsabilidades en la clase `AutoPilotImpl` introdujo un alto acoplamiento con la física de la locomotora y la lógica del modelo de simulación, resultando en un bucle `tick()` excesivamente complejo y propenso a fallos de sincronización y regresiones en los tests de integración.

## Decisión

Se decide simplificar drásticamente las responsabilidades del `AutoPilot` limitándolo estrictamente a la lógica de navegación topológica:

1. **Selección de Ramal (Forks)**:
   - Al entrar en cada segmento del itinerario, el `AutoPilot` debe decidir hacia qué ramal (siguiente segmento) se desvía el tren a la salida de dicho segmento.
   - Debe orientar el desvío (fork) correspondiente mediante la llamada a `ensureForkRoute`.

2. **Notificación de Ocupación**:
   - Si el siguiente segmento de la ruta calculada está ocupado (no libre), el `AutoPilot` se limita a disparar un evento de ocupación (`onSegmentOccupied`) a través de los listeners del tren.
   - El `AutoPilot` **no** detendrá directamente el tren ni modificará su velocidad objetivo. La frenada preventiva y los bloqueos de seguridad seguirán a cargo del `TrainSafetyManager`.

3. **Remoción de Lógica Excedente**:
   - Se elimina de `AutoPilotImpl` toda lógica relacionada con regulación de velocidad de crucero, paradas voluntarias, temporizadores de espera, inversión de marcha y procesos de carga/descarga de vagones.

## Consecuencias

- **Código Limpio y Mantenible**: El bucle de control del AutoPilot se reduce a unas pocas líneas que gestionan el cambio de waypoint, el cálculo de rutas A* y la alineación de desvíos en cambios de segmento.
- **Bajo Acoplamiento**: El AutoPilot ya no interfiere con el control físico de la velocidad ni los estados transicionales de carga/espera del tren.
- **Simplificación del Test Suite**: Se eliminan del plan de pruebas los casos relacionados con la regulación de velocidad e inversión de marcha del AutoPilot, centrándose exclusivamente en la correcta navegación por desvíos y la detección de tramos ocupados.
