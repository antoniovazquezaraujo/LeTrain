package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.mvp.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HUD command bar: errors red, short notices yellow (D1 contextual channel). */
@DisplayName("3D command bar text")
class Gdx3DHudCommandLineTest {

    @Test
    @DisplayName("an empty bar only shows the prompt")
    void emptyBar_showsPromptOnly() {
        Model model = mock(Model.class);
        when(model.getCommandText()).thenReturn("");
        when(model.getCommandError()).thenReturn("");
        when(model.getCommandNotice()).thenReturn("");

        String text = Gdx3DHud.commandLineText(model);

        assertEquals(":_", text);
        assertFalse(text.contains("[ERROR"));
        assertFalse(text.contains("[NOTICE"));
    }

    @Test
    @DisplayName("a syntax error stays red")
    void error_isRed() {
        Model model = mock(Model.class);
        when(model.getCommandText()).thenReturn("go nowhere;");
        when(model.getCommandError()).thenReturn("Syntax Error at 0:3");
        when(model.getCommandNotice()).thenReturn("");

        String text = Gdx3DHud.commandLineText(model);

        assertTrue(text.contains("[RED][ERROR: Syntax Error at 0:3][]"), text);
        assertFalse(text.contains("[NOTICE"), text);
    }

    @Test
    @DisplayName("a short notice is painted yellow after the prompt")
    void notice_isYellow() {
        Model model = mock(Model.class);
        when(model.getCommandText()).thenReturn("");
        when(model.getCommandError()).thenReturn("");
        when(model.getCommandNotice()).thenReturn("Train 99 not found; order ignored");

        String text = Gdx3DHud.commandLineText(model);

        assertTrue(text.startsWith(":_"), text);
        assertTrue(text.contains("[YELLOW][NOTICE: Train 99 not found; order ignored][]"), text);
        assertFalse(text.contains("[ERROR"), text);
    }
}
