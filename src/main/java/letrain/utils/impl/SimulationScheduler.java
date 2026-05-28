package letrain.utils.impl;

import java.util.ArrayList;
import java.util.List;

import letrain.utils.ValidationUtils;

/**
 * Implementación básica y segura del planificador de tareas por ticks.
 */
public class SimulationScheduler implements letrain.utils.SimulationScheduler {

    // Clase auxiliar interna para representar una tarea y sus ticks restantes
    private static class ScheduledTask {
        int remainingTicks;
        final Runnable action;

        ScheduledTask(int remainingTicks, Runnable action) {
            this.remainingTicks = remainingTicks;
            this.action = action;
        }
    }

    // Lista de tareas planificadas activas
    private final List<ScheduledTask> tasks = new ArrayList<>();

    @Override
    public void schedule(int ticksDelay, Runnable task) {
        ValidationUtils.requireNonNull(task, "task");
        ValidationUtils.requireNonNegative(ticksDelay, "ticksDelay");

        tasks.add(new ScheduledTask(ticksDelay, task));
    }

    @Override
    public void tick() {
        // Por implementar
    }

    @Override
    public void clear() {
        // Por implementar
    }
}