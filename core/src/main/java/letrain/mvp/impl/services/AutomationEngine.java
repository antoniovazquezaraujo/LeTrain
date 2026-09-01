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

    public List<String> setProgram(String program) {
        clearAllAutomationListeners();
        List<String> errors = new ArrayList<>();
        if (program == null || program.trim().isEmpty()) {
            return errors;
        }

        try {
            program = program.toLowerCase();
            CharStream input = CharStreams.fromString(program);
            LeTrainLexer lexer = new LeTrainLexer(input);
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
            CommandManager manager = new CommandManager(model);
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
