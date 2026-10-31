package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.MenuText;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** 2D menu painting from the shared menu model (issue #710). */
@DisplayName("2D menu painting")
class TerminalViewMenuTest {

    private static GameModeMenuOption option(String name, boolean enabled, boolean selected) {
        return new GameModeMenuOption(name, "description", () -> enabled, () -> selected,
                () -> Model.GameMode.RAILS);
    }

    @Test
    @DisplayName("a middle & paints the separated shortcut, colon and name in order")
    void should_PaintSeparatedShortcutAndName() {
        TextGraphics graphics = mock(TextGraphics.class);
        GameModeMenuOption sensors = option("S&ensors", true, true);

        int next = TerminalView.drawMenuOption(graphics, new TerminalPosition(0, 1), sensors, 1);

        InOrder order = inOrder(graphics);
        order.verify(graphics).setBackgroundColor(TerminalView.SELECTED_BG_COLOR);
        order.verify(graphics).setForegroundColor(TerminalView.SHORTCUT_COLOR);
        order.verify(graphics).putString(eq(new TerminalPosition(1, 1)), eq("E:"));
        order.verify(graphics).setForegroundColor(TerminalView.NORMAL_MENU_FG_COLOR);
        order.verify(graphics).putString(eq(new TerminalPosition(3, 1)), eq("Sensors"));
        order.verify(graphics).setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
        assertEquals(1 + "E:Sensors".length() + 1, next, "next option after the separating space");
    }

    @Test
    @DisplayName("a wrapped menu starts at row 0 so the info bar cannot overwrite its second row")
    void should_StartWrappedMenuAtTop() {
        assertEquals(1, TerminalView.menuStartRow(1), "single row keeps the blank top margin");
        assertEquals(0, TerminalView.menuStartRow(2), "two rows use the top margin");
        assertEquals(0, TerminalView.menuStartRow(3));
    }

    @Test
    @DisplayName("an explicit shortcut paints before the label: Planning + b -> B:Planning")
    void should_PaintExplicitShortcut() {
        TextGraphics graphics = mock(TextGraphics.class);
        GameModeMenuOption planning =
                new GameModeMenuOption("Planning", "b", "Plan routes and manage planned works",
                        () -> true, () -> false, () -> Model.GameMode.PLANNED_WORKS);

        int next = TerminalView.drawMenuOption(graphics, new TerminalPosition(0, 0), planning, 0);

        InOrder order = inOrder(graphics);
        order.verify(graphics).setForegroundColor(TerminalView.SHORTCUT_COLOR);
        order.verify(graphics).putString(eq(new TerminalPosition(0, 0)), eq("B:"));
        order.verify(graphics).setForegroundColor(TerminalView.NORMAL_MENU_FG_COLOR);
        order.verify(graphics).putString(eq(new TerminalPosition(2, 0)), eq("Planning"));
        assertEquals("B:Planning".length() + 1, next);
    }

    @Test
    @DisplayName("a disabled option is grey #808080 on shortcut and name, never the green shortcut")
    void should_PaintDisabledOptionInExactGrey() {
        TextGraphics graphics = mock(TextGraphics.class);
        GameModeMenuOption sensors = option("S&ensors", false, false);

        TerminalView.drawMenuOption(graphics, new TerminalPosition(0, 0), sensors, 0);

        TextColor.RGB grey = new TextColor.RGB(128, 128, 128);
        assertEquals(grey, TerminalView.MENU_DISABLED_FG_COLOR,
                "exact grey #808080, independent of the terminal theme (owner feedback)");
        InOrder order = inOrder(graphics);
        order.verify(graphics).setForegroundColor(grey);
        order.verify(graphics).putString(eq(new TerminalPosition(0, 0)), eq("E:"));
        order.verify(graphics).setForegroundColor(grey);
        order.verify(graphics).putString(eq(new TerminalPosition(2, 0)), eq("Sensors"));
        verify(graphics, never()).setForegroundColor(TerminalView.SHORTCUT_COLOR);
        verify(graphics, atLeastOnce()).setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
    }

    @Test
    @DisplayName("a label without & paints as plain white text")
    void should_PaintPlainLabel() {
        TextGraphics graphics = mock(TextGraphics.class);

        int next = TerminalView.drawMenuOption(graphics, new TerminalPosition(0, 0),
                option("Program", true, false), 0);

        verify(graphics).setForegroundColor(TerminalView.NORMAL_MENU_FG_COLOR);
        verify(graphics).putString(eq(new TerminalPosition(0, 0)), eq("Program"));
        assertEquals("Program".length() + 1, next);
    }

    @Test
    @DisplayName("every shared menu entry is painted from the parts of the shared parser")
    void should_PaintEveryEntryFromTheSharedParser() {
        List<GameModeMenuOption> menu = new letrain.mvp.impl.Model(1).getMenuModel();

        for (GameModeMenuOption menuOption : menu) {
            TextGraphics graphics = mock(TextGraphics.class);
            TerminalView.drawMenuOption(graphics, new TerminalPosition(0, 0), menuOption, 0);

            ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
            verify(graphics, atLeastOnce()).putString(any(), texts.capture());
            assertEquals(MenuText.displayText(menuOption.gameModeName(), menuOption.shortcut()),
                    String.join("", texts.getAllValues()), menuOption.gameModeName());
        }
    }
}
