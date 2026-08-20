 package letrain.utils;

    /**
     * Servicio de planificación de tareas reactivas basado en ticks del simulador.
     * Permite delegar la ejecución diferida de lógica sin necesidad de que las
     * locomotoras o managers individuales mantengan temporizadores en sus bucles
  de actualización.
     */
    public interface SimulationScheduler {

        /**
         * Planifica una tarea para ser ejecutada después de un número de ticks de
  simulación.
         *
         * @param ticksDelay Cantidad de ticks a esperar (ej: a 20 FPS, 20 ticks =
  1 segundo).
         * @param task       La acción a ejecutar tras expirar la espera.
         * @throws IllegalArgumentException si ticksDelay es negativo o task es
  nula.
         */
        void schedule(int ticksDelay, Runnable task);

        /**
         * Incrementa un tick del reloj interno y ejecuta las tareas cuya espera
  ha expirado.
         * Debe llamarse de forma centralizada en cada ciclo de la simulación.
         */
        void tick();

        /**
         * Cancela todas las tareas pendientes de ejecución.
         */
        void clear();
    }