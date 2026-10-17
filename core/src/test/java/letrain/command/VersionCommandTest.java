package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.BuildInfo;
import letrain.mvp.impl.Model;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #714: the running build must be easy to identify from the console, so a stale binary is
 * spotted immediately instead of after a confusing test.
 */
@DisplayName("Console version command (issue #714)")
class VersionCommandTest {

    /** Runs a command and returns the message the executor pushed to the UI (or null). */
    private static String run(Model model, String script) {
        String[] captured = {null};
        String error = PlayerCommandExecutor.execute(script, model, null, null, null,
                (title, text) -> captured[0] = text, null, null, null, null, null, false);
        assertNull(error, error);
        return captured[0];
    }

    @Test
    @DisplayName("version; reports the running build through the message sink")
    void version_reportsTheRunningBuild() {
        String text = run(new Model(1), "version;");

        assertEquals("LeTrain " + BuildInfo.versionTag(), text);
    }

    @Test
    @DisplayName("version without ';' works too: the console appends it")
    void version_withoutSemicolon_reportsTheRunningBuild() {
        String text = run(new Model(1), "version");

        assertEquals("LeTrain " + BuildInfo.versionTag(), text);
    }

    @Test
    @DisplayName("without a sink the visitor returns the text (data output, like time;)")
    void version_withoutSink_returnsTheText() {
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString("version;"));
        PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(lexer));
        PlayerCommandExecutor executor = new PlayerCommandExecutor(new Model(1));

        PlayerCommandsParser.VersionCommandContext ctx =
                parser.playerStart().playerStatement(0).versionCommand();
        Object result = executor.visit(ctx);

        assertEquals("LeTrain " + BuildInfo.versionTag(), result);
    }

    @Test
    @DisplayName("the version is data output: the journal/undo filter keeps it out")
    void version_isNotRecordable() {
        assertTrue(EditCommandFilter.isNonRecordable("version;"));
    }

    @Test
    @DisplayName("help version; documents the command")
    void helpVersion_documentsTheCommand() {
        String text = run(new Model(1), "help version;");

        assertTrue(text.contains("version;"), text);
    }

    @Test
    @DisplayName("a quoted 'version' still works as a name (reserved words must be quoted)")
    void quotedVersion_stillWorksAsAName() {
        String error = PlayerCommandExecutor.execute("save \"version\";", new Model(1), file -> {
        }, null, null);

        assertNull(error, error);
    }
}
