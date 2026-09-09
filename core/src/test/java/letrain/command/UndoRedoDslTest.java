package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code undo;}/{@code redo;} DSL commands (ADR-020 item 3): they must parse (with an
 * optional step count), invoke the wired undo/redo handler with the right number of steps, and never
 * be auto-recorded into the command journal (they are control commands, not edits).
 */
@DisplayName("DSL undo/redo commands")
class UndoRedoDslTest {

    private Model newModel() {
        Model m = new Model();
        m.updateGroundMap(new Point(-30, -30), 60, 60);
        m.getCursor().setPosition(new Point(0, 0));
        m.getCursor().setDir(Dir.E);
        return m;
    }

    @Test
    @DisplayName("undo; and redo; invoke the wired handlers with the requested step count")
    void undoRedo_parseAndInvokeHandlers() {
        Model model = newModel();

        int[] undoSteps = {0};
        int[] redoSteps = {0};
        String error = PlayerCommandExecutor.execute("undo 3; redo 2; undo;", model, null, null,
                null, null, null,
                steps -> undoSteps[0] += steps,
                steps -> redoSteps[0] += steps);
        assertNull(error, "undo/redo must parse and run cleanly, error=" + error);
        assertEquals(4, undoSteps[0], "undo 3 + undo 1 must reach the handler");
        assertEquals(2, redoSteps[0], "redo 2 must reach the handler");
    }

    @Test
    @DisplayName("undo/redo without a wired handler reports an error and does not mutate the world")
    void undoRedo_withoutHandler_returnsError() {
        Model model = newModel();
        String error = PlayerCommandExecutor.execute("undo;", model, null, null, null);
        assertEquals(true, error != null && error.contains("Undo is not supported"),
                "expected an explanatory error, got: " + error);
        assertEquals(0, model.getCommandJournal().size());
    }

    @Test
    @DisplayName("undo/redo control commands are never auto-recorded into the journal")
    void undoRedo_areNotJournaled() {
        Model model = newModel();
        // 'record on' first (whole line = control frame, excluded).
        assertNull(PlayerCommandExecutor.execute("record on;", model, null, null, null));
        // undo/redo lines must not be journaled.
        assertNull(PlayerCommandExecutor.execute("undo 2; redo;", model, null, null, null,
                null, null, steps -> {
                }, steps -> {
                }));
        assertEquals(0, model.getCommandJournal().size(),
                "undo/redo control commands must not be journaled");
        // A real command afterwards is journaled.
        assertNull(PlayerCommandExecutor.execute("mark 1;", model, null, null, null));
        assertEquals(1, model.getCommandJournal().size());
        assertEquals(true, model.getCommandJournal().entries().get(0).startsWith("mark"));
    }
}
