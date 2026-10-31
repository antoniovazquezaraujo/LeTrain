package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure formatting for the phase-1 planned-works dialog (construction roadmap): the table header,
 * one row per work, the total line and the selection clamp. The modal Lanterna window itself is not
 * driven here (it needs a real screen and blocks on input); the presentational strings are the part
 * that can be pinned deterministically.
 */
@DisplayName("Planned works dialog (construction roadmap, phase 1)")
class ConstructionPlansDialogTest {

    private static final ConstructionPlansDialog.Row CHEAPEST =
            new ConstructionPlansDialog.Row(1, "Borrador", "20,25", "34,254", "CHEAPEST",
                    "CHAPUCERT", "Low", 234, "$342.000", "12 min", "");
    private static final ConstructionPlansDialog.Row AVOID_ALL =
            new ConstructionPlansDialog.Row(2, "Borrador", "53,85", "92,329", "AVOID_ALL",
                    "ARTESANA", "High", 134, "$34.000", "3 min", "");
    private static final ConstructionPlansDialog.Row DIRECT = new ConstructionPlansDialog.Row(3,
            "Cancelada", "12,10", "44,80", "DIRECT", "-", "-", 98, "$9.800", "2 min", "");

    @Test
    @DisplayName("the header is the roadmap's compact table header")
    void should_ExposeTheExactHeader() {
        assertEquals(
                "#   State      From     To        Policy       Company     Quality  Cells  Cost         Time",
                ConstructionPlansDialog.header());
    }

    @Test
    @DisplayName("each row starts every value on the same column as the header")
    void should_AlignRowsWithTheHeader() {
        String line = ConstructionPlansDialog.formatRow(CHEAPEST);
        String header = ConstructionPlansDialog.header();

        assertEquals(header.indexOf("State"), line.indexOf("Borrador"));
        assertEquals(header.indexOf("From"), line.indexOf("20,25"));
        assertEquals(header.indexOf("To"), line.indexOf("34,254"));
        assertEquals(header.indexOf("Policy"), line.indexOf("CHEAPEST"));
        assertEquals(header.indexOf("Company"), line.indexOf("CHAPUCERT"));
        assertEquals(header.indexOf("Quality"), line.indexOf("Low"));
        // The cell count is the only numeric column: it is right-aligned under its header.
        assertEquals("234",
                line.substring(header.indexOf("Cells"), header.indexOf("Cells") + 5).trim());
        assertEquals(header.indexOf("Cost"), line.indexOf("$342.000"));
        assertEquals(header.indexOf("Time"), line.indexOf("12 min"));
        assertEquals(0, line.indexOf("1"));
    }

    @Test
    @DisplayName("the id is the # column and every planner field reaches the line")
    void should_FormatEveryColumn() {
        String line = ConstructionPlansDialog.formatRow(AVOID_ALL);

        assertTrue(line.startsWith("2   Borrador "), line);
        assertTrue(line.contains("53,85"), line);
        assertTrue(line.contains("92,329"), line);
        assertTrue(line.contains("AVOID_ALL"), line);
        assertTrue(line.contains("ARTESANA"), line);
        assertTrue(line.contains("High"), line);
        assertTrue(line.contains("$34.000"), line);
        assertTrue(line.contains("3 min"), line);
    }

    @Test
    @DisplayName("a warning is marked with a trailing bang; blank warnings are omitted")
    void should_MarkWarnings() {
        ConstructionPlansDialog.Row warned = new ConstructionPlansDialog.Row(4, "Borrador", "1,1",
                "2,2", "DIRECT", "-", "-", 2, "$100", "1 min", "track occupied");

        String line = ConstructionPlansDialog.formatRow(warned);
        assertTrue(line.endsWith("1 min !"), line);
        assertFalse(ConstructionPlansDialog.formatRow(CHEAPEST).endsWith("!"),
                "an empty warning must not add a mark");
    }

    @Test
    @DisplayName("the # column keeps the following columns aligned for two-digit ids")
    void should_AlignTwoDigitIds() {
        ConstructionPlansDialog.Row row = new ConstructionPlansDialog.Row(10, "Draft", "1,1", "2,2",
                "DIRECT", "-", "-", 2, "$100", "1 min", "");

        String line = ConstructionPlansDialog.formatRow(row);
        String header = ConstructionPlansDialog.header();

        assertEquals(header.indexOf("State"), line.indexOf("Draft"), line);
        assertEquals(header.indexOf("From"), line.indexOf("1,1"), line);
        assertTrue(line.startsWith("10 "), line);
    }

    @Test
    @DisplayName("the selected row keeps the selected style when the list loses focus")
    void should_KeepSelectedStyle_whenListIsNotFocused() {
        ActionListBox list = mock(ActionListBox.class);
        Theme theme = mock(Theme.class);
        ThemeDefinition definition = mock(ThemeDefinition.class);
        ThemeStyle selected = mock(ThemeStyle.class);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(list.getTheme()).thenReturn(theme);
        when(theme.getDefinition(AbstractListBox.class)).thenReturn(definition);
        when(definition.getSelected()).thenReturn(selected);
        when(graphics.getSize()).thenReturn(new TerminalSize(100, 1));

        ConstructionPlansDialog.rowRenderer(List.of(CHEAPEST)).drawItem(graphics, list, 0, () -> {
        }, true, false);

        verify(graphics).applyThemeStyle(selected);
        verify(graphics).putString(org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.argThat(label -> label != null
                        && label.startsWith(ConstructionPlansDialog.formatRow(CHEAPEST))));
    }

    @Test
    @DisplayName("Alt mnemonics map Add, Edit, Quotes, Delete and Close; Run needs a selected row")
    void should_MapAltMnemonics() {
        assertEquals(ConstructionPlansDialog.Action.ADD,
                ConstructionPlansDialog.shortcutAction('a', false));
        assertEquals(ConstructionPlansDialog.Action.ADD,
                ConstructionPlansDialog.shortcutAction('A', false));
        assertEquals(ConstructionPlansDialog.Action.EDIT,
                ConstructionPlansDialog.shortcutAction('e', true));
        assertEquals(ConstructionPlansDialog.Action.QUOTES,
                ConstructionPlansDialog.shortcutAction('q', true));
        assertEquals(ConstructionPlansDialog.Action.DELETE,
                ConstructionPlansDialog.shortcutAction('d', true));
        assertEquals(ConstructionPlansDialog.Action.CLOSE,
                ConstructionPlansDialog.shortcutAction('c', false));
        assertNull(ConstructionPlansDialog.shortcutAction('e', false));
        assertNull(ConstructionPlansDialog.shortcutAction('q', false));
        assertNull(ConstructionPlansDialog.shortcutAction('d', false));
        assertEquals(ConstructionPlansDialog.Action.RUN,
                ConstructionPlansDialog.shortcutAction('r', true));
        assertNull(ConstructionPlansDialog.shortcutAction('r', false));
    }

    @Test
    @DisplayName("the total line uses the roadmap wording and singular/plural")
    void should_FormatTheTotalLine() {
        assertEquals("Total: 3 works · $385.800 · 17 min",
                ConstructionPlansDialog.totalLine(3, "$385.800", "17 min"));
        assertEquals("Total: 1 work · $100 · 1 min",
                ConstructionPlansDialog.totalLine(1, "$100", "1 min"));
        assertEquals("Total: 0 works · $0 · 0 min",
                ConstructionPlansDialog.totalLine(0, "$0", "0 min"));
    }

    @Test
    @DisplayName("the dialog total aggregates the rows' already-formatted cost and time")
    void should_AggregateRows() {
        assertEquals("Total: 3 works · $385.800 · 17 min",
                ConstructionPlansDialog.totalLine(List.of(CHEAPEST, AVOID_ALL, DIRECT)));
        assertEquals("Total: 0 works · $0 · 0 min", ConstructionPlansDialog.totalLine(List.of()));
    }

    @Test
    @DisplayName("cost and time strings are read back in the canonical RoutePreviewStatus format")
    void should_ParseCanonicalFigures() {
        assertEquals(342000, ConstructionPlansDialog.parseCost("$342.000"));
        assertEquals(-1500, ConstructionPlansDialog.parseCost("-$1.500"));
        assertEquals(0, ConstructionPlansDialog.parseCost(""));
        assertEquals(12, ConstructionPlansDialog.parseMinutes("12 min"));
        assertEquals(60, ConstructionPlansDialog.parseMinutes("1 h"));
        assertEquals(90, ConstructionPlansDialog.parseMinutes("1 h 30 min"));
        assertEquals(0, ConstructionPlansDialog.parseMinutes("0 min"));
    }

    @Test
    @DisplayName("the remembered selection is clamped to the existing rows")
    void should_ClampTheSelection() {
        assertEquals(-1, ConstructionPlansDialog.clampIndex(0, 0));
        assertEquals(-1, ConstructionPlansDialog.clampIndex(5, 0));
        assertEquals(0, ConstructionPlansDialog.clampIndex(-3, 3));
        assertEquals(2, ConstructionPlansDialog.clampIndex(9, 3));
        assertEquals(1, ConstructionPlansDialog.clampIndex(1, 3));
    }

    @Test
    @DisplayName("the empty list keeps a clear Add/Close hint")
    void should_ExplainTheEmptyList() {
        String message = ConstructionPlansDialog.emptyMessage();
        assertTrue(message.contains("No planned works"), message);
        assertTrue(message.contains("[A] Add"), message);
    }

    @Test
    @DisplayName("the menu painters are gated while the Add flow hides the menu")
    void should_GateTheMenuPainters() {
        assertTrue(TerminalView.shouldPaintMenu(true, 1, 1));
        assertTrue(TerminalView.shouldPaintMenu(true, 2, 2));
        assertFalse(TerminalView.shouldPaintMenu(false, 2, 1));
        assertFalse(TerminalView.shouldPaintMenu(false, 2, 2));
        // Each part keeps its own help-level requirement even when visible.
        assertFalse(TerminalView.shouldPaintMenu(true, 1, 2));
        assertFalse(TerminalView.shouldPaintMenu(true, 0, 1));
    }

    @Test
    @DisplayName("hiding the menu gives the whole terminal to the game")
    void should_ReserveNoMenuRowsWhenHidden() {
        assertEquals(0, TerminalView.reservedMenuRows(false, 2));
        assertEquals(0, TerminalView.reservedMenuRows(false, 1));
        assertEquals(7, TerminalView.reservedMenuRows(true, 2));
        assertEquals(4, TerminalView.reservedMenuRows(true, 1));
        assertEquals(0, TerminalView.reservedMenuRows(true, 0));
    }
}
