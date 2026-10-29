package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

@DisplayName("Marks dialog: formatting, selection and shortcuts")
class MarksDialogTest {

    private static final MarksDialog.Row BASE = new MarksDialog.Row("base", "10,20");
    private static final MarksDialog.Row WAY = new MarksDialog.Row("Way-1-start", "34,254");

    @Test
    @DisplayName("rows and header share fixed name and position columns")
    void should_FormatRows() {
        String row = MarksDialog.formatRow(BASE);
        String header = MarksDialog.HEADER;

        assertEquals(header.indexOf("Position"), row.indexOf("10,20"), row);
        assertTrue(row.startsWith("base"), row);
    }

    @Test
    @DisplayName("marks that are too long are clipped to the table")
    void should_ClipLongFields() {
        String row = MarksDialog.formatRow(
                new MarksDialog.Row("a-very-long-mark-name-that-does-not-fit", "12345,67890"));

        assertEquals(36, row.length());
        assertTrue(row.contains("12345,67890"), row);
    }

    @Test
    @DisplayName("empty and total lines use the marks wording")
    void should_ShowEmptyAndTotals() {
        assertTrue(MarksDialog.emptyMessage().contains("No marks yet"));
        assertEquals("Total: 0 marks", MarksDialog.totalLine(0));
        assertEquals("Total: 1 mark", MarksDialog.totalLine(1));
        assertEquals("Total: 2 marks", MarksDialog.totalLine(2));
    }

    @Test
    @DisplayName("selection is clamped and Alt+G/D/C map to the actions")
    void should_ClampAndMapShortcuts() {
        assertEquals(-1, MarksDialog.clampIndex(0, 0));
        assertEquals(0, MarksDialog.clampIndex(-1, 2));
        assertEquals(1, MarksDialog.clampIndex(9, 2));
        assertEquals(MarksDialog.Action.GO, MarksDialog.shortcutAction('g', true));
        assertEquals(MarksDialog.Action.DELETE, MarksDialog.shortcutAction('d', true));
        assertEquals(MarksDialog.Action.CLOSE, MarksDialog.shortcutAction('c', false));
        assertNull(MarksDialog.shortcutAction('g', false));
        assertNull(MarksDialog.shortcutAction('d', false));
    }

    @Test
    @DisplayName("the selected mark keeps its selected style without focus")
    void should_KeepSelectedStyle() {
        ActionListBox list = mock(ActionListBox.class);
        Theme theme = mock(Theme.class);
        ThemeDefinition definition = mock(ThemeDefinition.class);
        ThemeStyle selected = mock(ThemeStyle.class);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(list.getTheme()).thenReturn(theme);
        when(theme.getDefinition(AbstractListBox.class)).thenReturn(definition);
        when(definition.getSelected()).thenReturn(selected);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 1));

        MarksDialog.rowRenderer(List.of(BASE)).drawItem(graphics, list, 0, () -> {
        }, true, false);

        verify(graphics).applyThemeStyle(selected);
        verify(graphics).putString(org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.argThat(
                        label -> label != null && label.startsWith(MarksDialog.formatRow(BASE))));
    }
}
