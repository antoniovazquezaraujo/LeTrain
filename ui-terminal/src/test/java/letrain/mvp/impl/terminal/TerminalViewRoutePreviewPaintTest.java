package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.googlecode.lanterna.TextColor;
import letrain.mvp.impl.terminal.RoutePreviewOverlay.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D route preview: cell colours")
class TerminalViewRoutePreviewPaintTest {

    private static final TextColor TERRAIN_BG = TextColor.ANSI.GREEN;

    @Test
    @DisplayName("normal route cells are white and keep the terrain background")
    void normalIsWhiteOnTerrain() {
        TerminalView.PreviewPaint paint =
                TerminalView.routePreviewPaint(false, Style.NORMAL, TERRAIN_BG);

        assertEquals(TextColor.ANSI.WHITE_BRIGHT, paint.foreground());
        assertEquals(TERRAIN_BG, paint.background());
    }

    @Test
    @DisplayName("tunnel/bridge cells are red and keep the terrain background")
    void specialIsRedOnTerrain() {
        TerminalView.PreviewPaint paint =
                TerminalView.routePreviewPaint(false, Style.SPECIAL, TERRAIN_BG);

        assertEquals(TextColor.ANSI.RED_BRIGHT, paint.foreground());
        assertEquals(TERRAIN_BG, paint.background());
    }

    @Test
    @DisplayName("endpoints stay yellow, keep the terrain background and win over special")
    void endpointsWin() {
        TerminalView.PreviewPaint paint =
                TerminalView.routePreviewPaint(true, Style.SPECIAL, TERRAIN_BG);

        assertEquals(TextColor.ANSI.YELLOW_BRIGHT, paint.foreground());
        assertEquals(TERRAIN_BG, paint.background());
    }
}
