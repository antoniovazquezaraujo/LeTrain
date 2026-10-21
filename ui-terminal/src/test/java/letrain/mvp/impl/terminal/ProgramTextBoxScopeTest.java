package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import letrain.command.GrammarReference;
import letrain.command.ProgramTextEdit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D editor: PROGRAM scopes do not gate the Scenario tab (#828)")
class ProgramTextBoxScopeTest {

    private static ProgramTextBox scenarioBox() {
        ProgramTextBox box =
                new ProgramTextBox(new TerminalSize(40, 10), "on build {\n  \n}\n", () -> {
                });
        // Inside the on build block, on its indented blank line.
        box.setCaretPosition(1, 2);
        return box;
    }

    @Test
    @DisplayName("Scenario passes null scope and inserts inside on build without a warning")
    void scenarioInsertsInsideBlock() {
        ProgramTextBox box = scenarioBox();

        String error = box.insertSnippet("go 0,0;",
                ProgramTextEdit.scopeForTab(false, GrammarReference.Scope.BODY_ONLY));

        assertNull(error);
        assertEquals("on build {\n  go 0,0;\n}", box.getText());
    }

    @Test
    @DisplayName("Programming still blocks a BODY_ONLY command inside a block")
    void programmingStillGatesBodyOnly() {
        ProgramTextBox box = scenarioBox();

        String error = box.insertSnippet("go 0,0;",
                ProgramTextEdit.scopeForTab(true, GrammarReference.Scope.BODY_ONLY));

        assertNotNull(error);
        assertTrue(error.contains("top level"));
        assertEquals("on build {\n  \n}", box.getText());
    }
}
