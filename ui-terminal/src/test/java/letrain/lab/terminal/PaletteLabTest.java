package letrain.lab.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Palette lab: ANSI fallback")
class PaletteLabTest {

    @Test
    @DisplayName("maps RGB colours to the nearest ANSI slot")
    void should_MapToNearestAnsiSlot() {
        assertEquals(TextColor.ANSI.BLACK, PaletteLab.ansi(0x000000));
        assertEquals(TextColor.ANSI.WHITE_BRIGHT, PaletteLab.ansi(0xFFFFFF));
        assertEquals(TextColor.ANSI.RED_BRIGHT, PaletteLab.ansi(0xFF0000));
        assertEquals(TextColor.ANSI.BLUE_BRIGHT, PaletteLab.ansi(0x4040FF));
        assertEquals(TextColor.ANSI.GREEN_BRIGHT, PaletteLab.ansi(0x00FF00));
    }
}
