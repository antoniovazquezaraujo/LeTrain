package letrain.lab.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    @DisplayName("cut mode switches regime instantly at the cut ratio")
    void should_CutRegime() {
        PaletteLab.Pal before = PaletteLab.blend(0, PaletteLab.CUT_RATIO - 0.01, true);
        PaletteLab.Pal after = PaletteLab.blend(0, PaletteLab.CUT_RATIO + 0.01, true);

        assertTrue(luminance(before.ground()) > luminance(after.ground()),
                "the paper must go dark in one step");
        assertEquals(PaletteLab.palettes()[0][2], after);
    }

    @Test
    @DisplayName("the light paper reaches its dusk tone at 20:00 and then fades")
    void should_ReachDuskTone_AtNightfall() {
        assertEquals(PaletteLab.ensureContrast(PaletteLab.palettes()[0][1]),
                PaletteLab.blend(0, PaletteLab.LIGHT_NIGHTFALL));
        PaletteLab.Pal fading = PaletteLab.blend(0, PaletteLab.LIGHT_NIGHTFALL + 0.05);
        assertTrue(fading.ground() != PaletteLab.palettes()[0][1].ground(),
                "the fade must already be visible");
    }

    @Test
    @DisplayName("the background darkens evenly, never a fully black frame")
    void should_DarkenEvenly_WithoutBlackFrame() {
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i <= 50; i++) {
            PaletteLab.Pal pal = PaletteLab.blend(0, PaletteLab.LIGHT_NIGHTFALL + i / 100.0);
            int ground = (int) luminance(pal.ground());
            assertTrue(ground != 0x000000, "the background must stay visible");
            assertTrue(ground <= previous, "the background must never brighten while darkening");
            previous = ground;
        }
    }

    @Test
    @DisplayName("every token keeps the minimum contrast across the whole fade")
    void should_KeepMinimumContrast_AcrossTheFade() {
        for (int i = 0; i <= 50; i++) {
            double ratio = PaletteLab.LIGHT_NIGHTFALL + i / 100.0;
            PaletteLab.Pal pal = PaletteLab.blend(0, ratio);
            double bg = luminance(pal.ground());
            int[] tokens = {pal.rail(), pal.water(), pal.station(), pal.sensor(), pal.label(),
                    pal.cursorDrawing()};
            for (int token : tokens) {
                double delta = Math.abs(luminance(token) - bg);
                assertTrue(delta >= PaletteLab.MIN_CONTRAST - 8,
                        "ratio=" + ratio + " delta=" + delta);
            }
        }
    }

    private static double luminance(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }

    @Test
    @DisplayName("colour interpolation is perceptual (linear light)")
    void should_MixColoursPerceptually() {
        assertEquals(0x000000, PaletteLab.mixColor(0x000000, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, PaletteLab.mixColor(0x000000, 0xFFFFFF, 1f));
        int mid = PaletteLab.mixColor(0x000000, 0xFFFFFF, 0.5f);
        assertTrue(mid > 0xAAAAAA && mid < 0xCCCCCC, "mid=" + Integer.toHexString(mid));
    }

    @Test
    @DisplayName("dump writes every token of both families and the current frame")
    void should_DumpPalette(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dump.txt");

        String path = new PaletteLab().dump(file);

        assertEquals(file.toString(), path);
        String text = Files.readString(file);
        assertTrue(text.contains("familia CLARA"));
        assertTrue(text.contains("familia OSCURA"));
        assertTrue(text.contains("crepúsculo"));
        assertTrue(text.contains("ground"));
        assertTrue(text.contains("cargoRuby"));
        assertTrue(text.contains("board"));
        assertTrue(text.contains("forkSelected"));
        assertTrue(text.contains("selectionLink"));
        assertTrue(text.contains("frame actual"));
        assertTrue(text.contains("ANSI"));
    }
}
