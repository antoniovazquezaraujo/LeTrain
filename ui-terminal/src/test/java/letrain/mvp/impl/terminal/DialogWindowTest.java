package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.PropertyTheme;
import com.googlecode.lanterna.gui2.DefaultWindowDecorationRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Shared chrome of the centered 2D dialogs (#742 polish): the frozen game frame used as the GUI
 * backdrop and the centered title of the one-line frame. The box geometry is pinned from both
 * dialogs' tests.
 */
@DisplayName("Centered 2D dialog chrome (#742 polish)")
class DialogWindowTest {

    @Test
    @DisplayName("the backdrop snapshots the visible frame and repaints it, clipping to the surface")
    void should_RepaintTheFrozenFrame() throws Exception {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(5, 3));
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCharacter(1, 1, new TextCharacter('X'));
        screen.setCharacter(3, 2, new TextCharacter('#'));
        screen.refresh();

        DialogWindow.FrozenBackground background = DialogWindow.snapshot(screen);
        screen.stopScreen();
        screen.close();

        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(5, 3));
        background.draw(graphics);

        ArgumentCaptor<TextCharacter> captured = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics).setCharacter(eq(1), eq(1), captured.capture());
        assertEquals('X', captured.getValue().getCharacter(),
                "the cell visible in the game frame survives into the dialog backdrop");
        verify(graphics).setCharacter(eq(3), eq(2), captured.capture());
        assertEquals('#', captured.getValue().getCharacter());
        // The whole captured frame is repainted, not just the non-blank cells of the game map.
        verify(graphics, times(5 * 3)).setCharacter(anyInt(), anyInt(), any(TextCharacter.class));

        // A smaller surface only gets the cells that fit: the snapshot is clipped, never wrapped.
        TextGUIGraphics small = mock(TextGUIGraphics.class);
        when(small.getSize()).thenReturn(new TerminalSize(2, 2));
        background.draw(small);
        verify(small, times(4)).setCharacter(anyInt(), anyInt(), any(TextCharacter.class));
    }

    @Test
    @DisplayName("missing front-buffer cells become the default character, so the backdrop never draws null")
    void should_DefaultNullCells() {
        Screen screen = mock(Screen.class);
        when(screen.getTerminalSize()).thenReturn(new TerminalSize(2, 1));
        when(screen.getFrontCharacter(0, 0)).thenReturn(new TextCharacter('A'));
        when(screen.getFrontCharacter(1, 0)).thenReturn(null);

        DialogWindow.FrozenBackground background = DialogWindow.snapshot(screen);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(2, 1));
        background.draw(graphics);

        ArgumentCaptor<TextCharacter> captured = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics).setCharacter(eq(1), eq(0), captured.capture());
        assertSame(Screen.DEFAULT_CHARACTER, captured.getValue(),
                "a null front-buffer cell is replaced by the screen default, not passed through");
        verify(graphics, times(2)).setCharacter(anyInt(), anyInt(), any(TextCharacter.class));
    }

    @Test
    @DisplayName("double-width characters survive the snapshot at their exact cell")
    void should_KeepWideCharacters() throws Exception {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(4, 2));
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCharacter(1, 0, new TextCharacter('界'));
        screen.refresh();

        DialogWindow.FrozenBackground background = DialogWindow.snapshot(screen);
        screen.stopScreen();
        screen.close();

        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(4, 2));
        background.draw(graphics);

        ArgumentCaptor<TextCharacter> captured = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics).setCharacter(eq(1), eq(0), captured.capture());
        assertEquals('界', captured.getValue().getCharacter());
        assertTrue(captured.getValue().isDoubleWidth(),
                "the wide glyph keeps its width when repainted as the backdrop");
    }

    @Test
    @DisplayName("the dialog theme centers the title on the frame")
    void should_CenterTheWindowTitle() {
        assertTrue(
                NewGameDialog.darkTheme(new PropertyTheme(new Properties()))
                        .getDefinition(DefaultWindowDecorationRenderer.class)
                        .getBooleanProperty("CENTER_TITLE", false),
                "DefaultWindowDecorationRenderer centers titles only with CENTER_TITLE");
    }
}
