package letrain.command;

import letrain.mvp.Model;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class PlayerCommandExecutor extends PlayerCommandsParserBaseVisitor<Object> {
    private static final Logger log = LoggerFactory.getLogger(PlayerCommandExecutor.class);
    private Model model;

    public PlayerCommandExecutor(Model model) {
        this.model = model;
    }

    public static String execute(String commandText, Model model) {
        if (!commandText.trim().endsWith(";")) {
            commandText = commandText + ";";
        }
        
        List<String> errors = new ArrayList<>();
        PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(new LeTrainLexer(CharStreams.fromString(commandText))));
        parser.removeErrorListeners();
        parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
                errors.add("Syntax Error at " + charPositionInLine + ": " + msg);
            }
        });

        PlayerCommandsParser.PlayerStartContext tree = parser.playerStart();
        if (!errors.isEmpty()) {
            return String.join("\n", errors);
        }

        try {
            PlayerCommandExecutor executor = new PlayerCommandExecutor(model);
            executor.visit(tree);
            return null; // No errors
        } catch (Exception e) {
            log.error("Command execution error", e);
            return e.getMessage();
        }
    }

    @Override
    public Object visitStatement(PlayerCommandsParser.StatementContext ctx) {
        // Since it's a script logic statement, we can re-parse it with ScriptLogicParser
        // This avoids duplicating all the visitor logic for trains, semaphores, etc.
        // Wait, ANTLR4 tokens contain the start and stop index!
        int start = ctx.getStart().getStartIndex();
        int stop = ctx.getStop().getStopIndex();
        String originalText = ctx.getStart().getInputStream().getText(new org.antlr.v4.runtime.misc.Interval(start, stop));
        
        ScriptLogicParser scriptParser = new ScriptLogicParser(new CommonTokenStream(new LeTrainLexer(CharStreams.fromString(originalText))));
        ScriptLogicParser.ScriptStartContext scriptTree = scriptParser.scriptStart();
        CommandManager scriptManager = new CommandManager(model);
        scriptManager.visit(scriptTree);
        return null;
    }

    @Override
    public Object visitGoCommand(PlayerCommandsParser.GoCommandContext ctx) {
        int x = Integer.parseInt(ctx.NUMBER(0).getText());
        int y = Integer.parseInt(ctx.NUMBER(1).getText());
        model.getCursor().getPosition().setX(x);
        model.getCursor().getPosition().setY(y);
        return null;
    }

    @Override
    public Object visitNewCommand(PlayerCommandsParser.NewCommandContext ctx) {
        throw new RuntimeException("Command 'new' not yet implemented in UI");
    }

    @Override
    public Object visitDelCommand(PlayerCommandsParser.DelCommandContext ctx) {
        throw new RuntimeException("Command 'del' not yet implemented in UI");
    }

    @Override
    public Object visitModeCommand(PlayerCommandsParser.ModeCommandContext ctx) {
        throw new RuntimeException("Command 'mode' not yet implemented in UI");
    }

    @Override
    public Object visitSaveCommand(PlayerCommandsParser.SaveCommandContext ctx) {
        throw new RuntimeException("Command 'save' not yet implemented in UI");
    }

    @Override
    public Object visitLoadCommand(PlayerCommandsParser.LoadCommandContext ctx) {
        throw new RuntimeException("Command 'load' not yet implemented in UI");
    }
}
