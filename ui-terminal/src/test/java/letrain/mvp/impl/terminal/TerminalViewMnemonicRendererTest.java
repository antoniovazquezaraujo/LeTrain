package letrain.mvp.impl.terminal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Terminal mnemonics: disabled buttons stay readable")
class TerminalViewMnemonicRendererTest {

    @Test
    @DisplayName("a disabled button paints the whole label with the disabled foreground")
    void disabledButton_paintsMnemonicsInDisabledColour() {
        Button button = mock(Button.class);
        ThemeDefinition definition = mock(ThemeDefinition.class);
        ThemeStyle insensitive = mock(ThemeStyle.class);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(button.isEnabled()).thenReturn(false);
        when(button.getLabel()).thenReturn("Edit");
        when(button.getThemeDefinition()).thenReturn(definition);
        when(definition.getInsensitive()).thenReturn(insensitive);

        TerminalView.mnemonicRenderer('E').drawComponent(graphics, button);

        verify(graphics).applyThemeStyle(insensitive);
        verify(graphics).putString(0, 0, "< Edit >");
        verify(graphics).setForegroundColor(TerminalView.MENU_DISABLED_FG_COLOR);
        verify(graphics).putString(2, 0, "E");
    }

    @Test
    @DisplayName("an enabled button keeps the red mnemonic")
    void enabledButton_keepsMnemonicAccent() {
        Button button = mock(Button.class);
        ThemeDefinition definition = mock(ThemeDefinition.class);
        ThemeStyle normal = mock(ThemeStyle.class);
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(button.isEnabled()).thenReturn(true);
        when(button.isFocused()).thenReturn(false);
        when(button.getLabel()).thenReturn("Delete");
        when(button.getThemeDefinition()).thenReturn(definition);
        when(definition.getNormal()).thenReturn(normal);

        TerminalView.mnemonicRenderer('D').drawComponent(graphics, button);

        verify(graphics).applyThemeStyle(normal);
        verify(graphics).setForegroundColor(TerminalView.MNEMONIC_COLOR);
        verify(graphics).putString(2, 0, "D");
    }
}
