package letrain.command;

import java.util.ArrayList;
import java.util.List;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;

/**
 * Builds a scenario text from the live model (ADR-020 item 4). The {@code on build} section comes
 * from the recorded command journal; {@code on start} is derived from the world's initial state
 * (currently closed semaphores) so replaying the scenario starts from the same configuration.
 *
 * <p>
 * UI-free and side-effect free; the presenters call it on export.
 */
public final class ScenarioExporter {

    private ScenarioExporter() {}

    /** Initial-state commands derived from the current model (semaphore open/closed states). */
    public static List<String> initialConditions(Model model) {
        List<String> start = new ArrayList<>();
        if (model != null && model.getSemaphores() != null) {
            for (RailSemaphore semaphore : model.getSemaphores()) {
                if (semaphore != null) {
                    start.add("semaphore " + semaphore.getId() + (semaphore.isOpen() ? " open;" : " close;"));
                }
            }
        }
        return start;
    }

    /** Renders the full scenario (seed + on build from the journal + on start initial state). */
    public static String render(Model model, List<String> buildCommands) {
        return ScenarioFile.render(model.getSeed(), buildCommands, initialConditions(model));
    }
}
