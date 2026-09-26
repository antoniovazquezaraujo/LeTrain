package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.ArrayList;
import java.util.List;
import letrain.command.CommandManager;
import letrain.command.LeTrainLexer;
import letrain.command.ScriptLogicParser;
import letrain.mvp.impl.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
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
        return applyProgram(program, model.getUserMessageSink() != null);
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
        return applyProgram(program, true);
    }

    private List<String> applyProgram(String program, boolean userChannel) {
        List<String> errors = new ArrayList<>();
        if (program == null || program.trim().isEmpty()) {
            clearAllAutomationListeners();
            return errors;
        }

        try {
            program = program.toLowerCase();
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
            CommandManager manager = new CommandManager(model);
            if (userChannel) {
                // Route through the model, not a captured sink: reportUserMessage resolves the
                // client's current channel and queues the notice while no sink is wired yet. The
                // disk path wires even when headless, where it can only queue: its program is a
                // savegame's, re-applied before any caller notifier exists (missionNotifier is
                // transient and a loaded train starts without one).
                manager.setWarningSink(model::reportUserMessage);
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
}
