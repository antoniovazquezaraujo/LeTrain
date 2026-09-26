package letrain.mvp.impl.terminal;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Command-bar painting: errors red, short notices yellow (D1 contextual channel). */
@DisplayName("2D command bar painting")
class TerminalViewCommandLineTest {

    private static final TerminalSize SIZE = new TerminalSize(80, 3);

    @Test
    @DisplayName("a short notice is painted yellow after the prompt")
    void notice_isPaintedYellow() {
        TextGraphics g = mock(TextGraphics.class);

        TerminalView.drawCommandLine(g, SIZE, "train 99 set speed 3;", "",
                "Train 99 not found; order ignored");

        String prompt = ":train 99 set speed 3;_";
        verify(g).putString(eq(0), eq(SIZE.getRows() - 1), eq(prompt));
        verify(g).putString(eq(prompt.length() + 2), eq(SIZE.getRows() - 1),
                contains("Train 99 not found; order ignored"));
        verify(g).setBackgroundColor(TextColor.ANSI.YELLOW);
        verify(g).setForegroundColor(TextColor.ANSI.BLACK);
    }

    @Test
    @DisplayName("a syntax error keeps its red form and does not paint a notice")
    void error_keepsRedForm() {
        TextGraphics g = mock(TextGraphics.class);

        TerminalView.drawCommandLine(g, SIZE, "go nowhere;", "Syntax Error at 0:3", null);

        verify(g).setBackgroundColor(TextColor.ANSI.RED);
        verify(g, never()).setBackgroundColor(TextColor.ANSI.YELLOW);
    }

    @Test
    @DisplayName("error and notice, when both are present, are painted side by side")
    void errorAndNotice_arePaintedSideBySide() {
        TextGraphics g = mock(TextGraphics.class);

        TerminalView.drawCommandLine(g, SIZE, "x", "ERR", "NOTICE");

        String prompt = ":x_";
        int errorX = prompt.length() + 2;
        int noticeX = errorX + " ERR ".length() + 1;
        verify(g).putString(eq(errorX), eq(SIZE.getRows() - 1), eq(" ERR "));
        verify(g).putString(eq(noticeX), eq(SIZE.getRows() - 1), eq(" NOTICE "));
    }
}
