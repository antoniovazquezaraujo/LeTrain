package letrain.command;

import java.util.ArrayList;
import java.util.List;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;
import letrain.track.SpeedSignal;
import letrain.track.rail.ForkRailTrack;

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

    /**
     * Initial-state commands derived from the current model: semaphore open/closed, fork routes and
     * speed-signal mode/limit. These are the element states that a rebuilt world must restore so the
     * scenario starts exactly as it was exported (they are not always captured by the journal, e.g.
     * when changed outside the Record mode or altered at runtime by triggers).
     */
    public static List<String> initialConditions(Model model) {
        List<String> start = new ArrayList<>();
        if (model == null) {
            return start;
        }
        if (model.getSemaphores() != null) {
            for (RailSemaphore semaphore : model.getSemaphores()) {
                if (semaphore != null) {
                    start.add("semaphore " + semaphore.getId()
                            + (semaphore.isOpen() ? " open;" : " close;"));
                }
            }
        }
        if (model.getForks() != null) {
            for (ForkRailTrack fork : model.getForks()) {
                if (fork != null) {
                    start.add("fork " + fork.getId()
                            + (fork.isUsingAlternativeRoute() ? " set curved;" : " set straight;"));
                }
            }
        }
        if (model.getSpeedSignals() != null) {
            for (SpeedSignal signal : model.getSpeedSignals()) {
                if (signal != null) {
                    start.add("signal " + signal.getId() + " set mode "
                            + (signal.isMax() ? "max;" : "min;"));
                    start.add("signal " + signal.getId() + " set limit " + signal.getLimit() + ";");
                }
            }
        }
        return start;
    }

    /** Renders the full scenario (seed + settings + on build + on start + the program). */
    public static String render(Model model, List<String> buildCommands) {
        return ScenarioFile.render(model.getSeed(), configuration(model), buildCommands,
                initialConditions(model), model.getProgram());
    }

    /** Renders the editable world recipe (seed + on build + on start), without settings or program. */
    public static String renderWorld(Model model, List<String> buildCommands) {
        return ScenarioFile.render(model.getSeed(), null, buildCommands,
                initialConditions(model), null);
    }

    /** The model's effective settings (so the scenario reproduces terrain and rules), or null. */
    private static java.util.Map<String, String> configuration(Model model) {
        return model != null && model.getEconomyManager() != null
                ? model.getEconomyManager().effectiveConfig()
                : null;
    }
}
