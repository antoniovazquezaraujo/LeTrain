package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import java.util.List;
import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.WorkQuote;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Quotes dialog: company comparison and availability")
class QuotesDialogTest {

    private static WorkQuote normalQuote() {
        return WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, RoutePlan.feasible(
                List.of(new RouteStep(new Point(0, 0), TrackType.NORMAL_TRACK)), 100, 10));
    }

    private static WorkQuote bridgeQuote() {
        return WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, RoutePlan.feasible(
                List.of(new RouteStep(new Point(0, 0), TrackType.BRIDGE_TRACK)), 100, 10));
    }

    @Test
    @DisplayName("a quote row shows company, cost, time, quality and its capabilities")
    void should_FormatAQuoteRow() {
        String row = QuotesDialog.formatRow(normalQuote());
        String[] cells = row.split("\\|", -1);

        assertEquals(5, cells.length, row);
        assertEquals("IMPERIAL", cells[0].trim(), row);
        assertEquals("$250", cells[1].trim(), row);
        assertEquals("1 min", cells[2].trim(), row);
        assertEquals("High", cells[3].trim(), row);
        assertEquals("Bridge+Tunnel", cells[4].trim(), row);
    }

    @Test
    @DisplayName("an unavailable company is marked and has no cost/time")
    void should_MarkUnavailableQuotes() {
        String row = QuotesDialog.formatRow(bridgeQuote());
        String[] cells = row.split("\\|", -1);

        assertEquals("CHAPUCERT", cells[0].trim(), row);
        assertEquals("-", cells[1].trim(), row);
        assertEquals("-", cells[2].trim(), row);
        assertTrue(cells[4].trim().startsWith("-"), row);
        assertTrue(row.endsWith("!"), row);
        assertTrue(QuotesDialog.unavailableLine(List.of(bridgeQuote()))
                .contains("cannot build bridges"));
    }

    @Test
    @DisplayName("j/k move the company table selection and wrap around the ends")
    void should_MoveSelectionWithJAndK() {
        ActionListBox list = mock(ActionListBox.class);
        when(list.getSelectedIndex()).thenReturn(0, 2, -1);

        QuotesDialog.moveSelection(list, 3, 1);
        QuotesDialog.moveSelection(list, 3, 1);
        QuotesDialog.moveSelection(list, 3, -1);

        verify(list).setSelectedIndex(1);
        verify(list).setSelectedIndex(0);
        verify(list).setSelectedIndex(2);
        verify(list, org.mockito.Mockito.times(3)).takeFocus();
    }

    @Test
    @DisplayName("the selection is clamped and the selected row keeps its colour without focus")
    void should_ClampAndKeepSelection() {
        assertEquals(-1, QuotesDialog.clampIndex(0, 0));
        assertEquals(0, QuotesDialog.clampIndex(-1, 2));
        assertEquals(1, QuotesDialog.clampIndex(5, 2));

        ActionListBox list = mock(ActionListBox.class);
        Theme theme = mock(Theme.class);
        ThemeDefinition definition = mock(ThemeDefinition.class);
        ThemeStyle selected = mock(ThemeStyle.class);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(list.getTheme()).thenReturn(theme);
        when(theme.getDefinition(AbstractListBox.class)).thenReturn(definition);
        when(definition.getSelected()).thenReturn(selected);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 1));

        QuotesDialog.rowRenderer(List.of(normalQuote())).drawItem(graphics, list, 0, () -> {
        }, true, false);

        verify(graphics).applyThemeStyle(selected);
    }
}
