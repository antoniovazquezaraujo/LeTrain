package letrain.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746 (D1): the console command {@code new game;} is part of the player grammar and reaches
 * the client's New Game flow through a callback. Without a client able to show the dialog the
 * command reports itself as unavailable instead of silently doing nothing.
 */
@DisplayName("console command: new game; (#746)")
class PlayerCommandExecutorNewGameTest {

    private static final java.util.function.Consumer<java.io.File> NO_FILE = file -> {
    };

    private static String run(String command, Model model, Runnable onNewGame) {
        return PlayerCommandExecutor.execute(command, model, NO_FILE, NO_FILE, null, null, null,
                null, null, NO_FILE, NO_FILE, onNewGame, false);
    }

    @Test
    @DisplayName("`new game;` invokes the client's New Game callback")
    void should_InvokeTheNewGameCallback() {
        Model model = new Model(1);
        boolean[] called = {false};

        String error = run("new game;", model, () -> called[0] = true);

        assertNull(error);
        assertTrue(called[0], "the callback must open the New Game flow");
    }

    @Test
    @DisplayName("extra spaces and a missing semicolon still match (keywords stay lowercase)")
    void should_AcceptTolerantSpellings() {
        Model model = new Model(1);
        int[] calls = {0};
        Runnable callback = () -> calls[0]++;

        assertNull(run("new game", model, callback));
        assertNull(run("  new   game ;  ", model, callback));

        assertTrue(calls[0] == 2, "both spellings must reach the callback, got " + calls[0]);
    }

    @Test
    @DisplayName("without a New Game client the command reports it as unavailable")
    void should_ReportWhenThereIsNoClient() {
        Model model = new Model(1);

        String error = run("new game;", model, null);

        assertNotNull(error);
        assertTrue(error.contains("not available"), error);
    }

    @Test
    @DisplayName("other `new` commands keep their meaning")
    void should_KeepTheOtherNewCommands() {
        Model model = new Model(1);
        int[] calls = {0};

        run("new sg;", model, () -> calls[0]++);

        assertTrue(calls[0] == 0, "`new sg;` must not open the New Game dialog");
    }
}
