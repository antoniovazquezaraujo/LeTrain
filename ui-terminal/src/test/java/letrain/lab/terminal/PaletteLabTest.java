package letrain.lab.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("day-night ratio follows the game clock curve")
    void should_ComputeRatio() {
        assertEquals(0.0, PaletteLab.ratioOf(12.0), 1e-9);
        assertEquals(0.5, PaletteLab.ratioOf(20.0), 1e-9);
        assertEquals(1.0, PaletteLab.ratioOf(23.0), 1e-9);
        assertEquals(0.5, PaletteLab.ratioOf(6.0), 1e-9);
    }

    @Test
    @DisplayName("blend returns the key palettes at the ends")
    void should_BlendKeyPalettes() {
        assertEquals(PaletteLab.palettes()[0][0], PaletteLab.blend(0, 0.0));
        assertEquals(PaletteLab.palettes()[0][2], PaletteLab.blend(0, 1.0));
        assertEquals(PaletteLab.palettes()[1][1], PaletteLab.blend(1, 0.5));
    }

    @Test
    @DisplayName("the light paper reaches its dusk tone at 20:00 and then fades")
    void should_ReachDuskTone_AtNightfall() {
        assertEquals(PaletteLab.palettes()[0][1], PaletteLab.blend(0, PaletteLab.LIGHT_NIGHTFALL));
        PaletteLab.Pal fading = PaletteLab.blend(0, PaletteLab.LIGHT_NIGHTFALL + 0.05);
        assertTrue(fading.ground() != PaletteLab.palettes()[0][1].ground(),
                "the fade must already be visible");
    }

    @Test
    @DisplayName("the light family reaches a deep twilight, never a fully black frame")
    void should_ReachTwilight_AtNightfall() {
        double midFade = (PaletteLab.LIGHT_NIGHTFALL + 1.0) / 2.0;

        PaletteLab.Pal faded = PaletteLab.blend(0, midFade);

        assertEquals(PaletteLab.TWILIGHT, faded);
        assertTrue(faded.ground() != 0x000000, "the background must stay visible");
        assertTrue(luminance(faded.rail()) - luminance(faded.ground()) > 40,
                "the rail must keep contrast against the ground");
    }

    private static int luminance(int rgb) {
        return (int) (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF)
                + 0.0722 * (rgb & 0xFF));
    }

    @Test
    @DisplayName("colour interpolation is perceptual (linear light)")
    void should_MixColoursPerceptually() {
        assertEquals(0x000000, PaletteLab.mixColor(0x000000, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, PaletteLab.mixColor(0x000000, 0xFFFFFF, 1f));
        int mid = PaletteLab.mixColor(0x000000, 0xFFFFFF, 0.5f);
        assertTrue(mid > 0xAAAAAA && mid < 0xCCCCCC, "mid=" + Integer.toHexString(mid));
    }
}
