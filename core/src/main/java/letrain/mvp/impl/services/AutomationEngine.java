package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.ArrayList;
import java.util.List;
import letrain.command.CommandManager;
import letrain.command.LeTrainLexer;
import letrain.command.ScriptLogicParser;
import letrain.itinerary.AutoPilot;
import letrain.itinerary.TrainMission;
import letrain.mvp.impl.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates the logic for parsing and executing LeTrain automation programs. */
@JsonIgnoreType
public class AutomationEngine {
    private static final Logger log = LoggerFactory.getLogger(AutomationEngine.class);
    private final Model model;

    public AutomationEngine(Model model) {
        this.model = model;
    }

    /**
     * Applies a program built in the console or the program editor. Its problem notices (unknown
     * entity, dropped waypoint, clamped speed…) reach the model's visible channel when the client
     * has wired its sink; without one they stay log-only and a caller-provided mission notifier is
     * left in place, so headless callers keep working (D1 contract).
     */
    public List<String> setProgram(String program) {
        return applyProgram(program, model.getUserMessageSink() != null, true);
    }

    /**
     * Applies a program that came from disk (a savegame, a program file — see
     * {@link Model#setProgramFromDisk}). A savegame's program is re-applied by
     * {@link Model#postLoadInit} while the presenter (and its sink) does not exist yet, so its
     * notices always go through {@link Model#reportUserMessage}: delivered to the client's current
     * channel, or queued until one is wired. A valid program's semantic warnings must not stay in
     * the log (D1 residual).
     */
    public List<String> setProgramFromDisk(String program) {
        return applyProgram(program, true, false);
    }

    private List<String> applyProgram(String program, boolean userChannel, boolean resetRunning) {
        List<String> errors = new ArrayList<>();
        if (program == null || program.trim().isEmpty()) {
            clearAllAutomationListeners();
            if (resetRunning) {
                resetAutomationState(userChannel);
            }
            return errors;
        }

        try {
            // The program runs verbatim (D2 strict case): keywords stay lowercase, strings and
            // names keep the case the author wrote. A wrong-case keyword is a syntax error with
            // line:column and nothing executes (the check below returns before visiting).
            CharStream input = CharStreams.fromString(program);
            LeTrainLexer lexer = new LeTrainLexer(input);
            lexer.removeErrorListeners();
            lexer.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
                @Override
                public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
                        Object offendingSymbol, int line, int charPositionInLine, String msg,
                        org.antlr.v4.runtime.RecognitionException e) {
                    String errorMsg =
                            "Lexer error at line " + line + ":" + charPositionInLine + " " + msg;
                    log.error(errorMsg);
                    errors.add(errorMsg);
                }
            });
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ScriptLogicParser parser = new ScriptLogicParser(tokens);

            parser.removeErrorListeners();
            parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
                @Override
                public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
                        Object offendingSymbol, int line, int charPositionInLine, String msg,
                        org.antlr.v4.runtime.RecognitionException e) {
                    String errorMsg =
                            "Syntax error at line " + line + ":" + charPositionInLine + " " + msg;
                    log.error(errorMsg);
                    errors.add(errorMsg);
                }
            });

            ScriptLogicParser.ScriptStartContext sintaxTree = parser.scriptStart();
            if (!errors.isEmpty()) {
                // D1: lexer/syntax errors never execute (not even partially). The previously
                // applied automation stays in place until a valid program replaces it.
                return errors;
            }
            clearAllAutomationListeners();
            if (resetRunning) {
                resetAutomationState(userChannel);
            }
            CommandManager manager = new CommandManager(model);
            if (userChannel) {
                // Route through the model, not a captured sink: reportUserMessage resolves the
                // client's current channel and queues the notice while no sink is wired yet. The
                // disk path wires even when headless, where it can only queue: its program is a
                // savegame's, re-applied before any caller notifier exists (missionNotifier is
                // transient and a loaded train starts without one).
                manager.setWarningSink(model::reportUserMessage);
                // Deferred blocks (triggers) and mission notices are asynchronous: panel too.
                manager.setDeferredWarningSink(model::reportUserMessage);
            }
            manager.visit(sintaxTree);
        } catch (Exception e) {
            log.error("Error parsing or executing automation program", e);
            errors.add("Critical error: " + e.getMessage());
        }
        return errors;
    }

    private void clearAllAutomationListeners() {
        model.getSensors().forEach(Sensor::removeAllSensorEventListeners);
        model.getStations().forEach(Station::removeAllStationEventListeners);
        model.getForks().forEach(ForkRailTrack::removeAllForkEventListeners);
        model.getSemaphores().forEach(RailSemaphore::removeAllSemaphoreEventListeners);
        model.removeAllScriptTrainEventListeners();
    }

    /**
     * Wipes the per-train automation state before the new program builds it again (ADR-009: every
     * APPLY wipes and recreates the state). A cancelled mission plus a reset of the pending
     * waypoint actions keeps a half-done maneuver from resuming against the new plan, and clearing
     * the block wait rescues a train stuck on a release that will never come (#653). A train that
     * was running is announced through the user channel: the new program restarts its service, so a
     * hot swap is never silent.
     */
    private void resetAutomationState(boolean userChannel) {
        for (Train train : trains()) {
            boolean wasRunning = train.isAutoMode() && isAutomationActive(train);
            if (wasRunning) {
                // Safe stop: the new program re-evaluates the movement from a stopped train. The
                // deferred speed of the old program is dropped below so it cannot come back.
                train.getMovementManager().initiateBraking();
            }
            if (train.getActionManager() != null) {
                train.getActionManager().resetPendingActions();
            }
            if (train.getAutopilot() != null) {
                train.getAutopilot().deactivate();
            }
            if (train.getSafetyManager() != null) {
                train.getSafetyManager().cancelBlockWait();
            }
            train.setSavedTargetSpeed(-1);
            if (wasRunning) {
                reportProgramReset(train, userChannel);
            }
        }
    }

    private static boolean isAutomationActive(Train train) {
        AutoPilot autopilot = train.getAutopilot();
        if (autopilot == null) {
            return false;
        }
        return autopilot.mode() != AutoPilot.Mode.IDLE
                || autopilot.mission().filter(TrainMission::isActive).isPresent();
    }

    private void reportProgramReset(Train train, boolean userChannel) {
        String text = "Train " + train.getId()
                + " was running: the new program reset its automation (a reassigned itinerary"
                + " restarts from the first waypoint). Stop the train before reprogramming to"
                + " avoid restarting a maneuver in progress.";
        if (userChannel) {
            model.reportUserMessage("Program", text);
        } else {
            log.warn("[DSL] {}", text);
        }
    }

    /** Every train of the model, each one once even when it carries several locomotives. */
    private List<Train> trains() {
        List<Train> trains = new ArrayList<>();
        for (Locomotive locomotive : model.getLocomotives()) {
            Train train = locomotive.getTrain();
            if (train != null && !trains.contains(train)) {
                trains.add(train);
            }
        }
        return trains;
    }
}
