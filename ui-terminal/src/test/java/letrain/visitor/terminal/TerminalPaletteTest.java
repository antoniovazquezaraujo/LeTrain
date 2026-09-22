package letrain.visitor.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import java.util.Map;
import letrain.visitor.terminal.TerminalPalette.Depth;
import letrain.visitor.terminal.TerminalPalette.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Terminal day/night palette")
class TerminalPaletteTest {

    @Test
    @DisplayName("day and night ends are paper and dark board")
    void should_ResolveEnds() {
        Map<Token, Integer> day = TerminalPalette.rgbFor(0f);
        Map<Token, Integer> night = TerminalPalette.rgbFor(1f);

        assertEquals(0xF2F0E8, day.get(Token.BOARD));
        assertEquals(0xF2F0E8, day.get(Token.GROUND));
        assertEquals(0x285ABE, day.get(Token.WATER));
        assertEquals(0x161923, night.get(Token.BOARD));
        assertEquals(0x9696A0, night.get(Token.RAIL));
    }

    @Test
    @DisplayName("every token keeps the minimum contrast against the board")
    void should_KeepContrast_When_Blending() {
        for (float ratio = 0f; ratio <= 1f; ratio += 0.05f) {
            Map<Token, Integer> rgb = TerminalPalette.rgbFor(ratio);
            double board = TerminalPalette.luminance(rgb.get(Token.BOARD));
            for (Token token : Token.values()) {
                if (token == Token.BOARD || token == Token.GROUND) {
                    continue;
                }
                double distance = Math.abs(TerminalPalette.luminance(rgb.get(token)) - board);
                assertTrue(distance >= TerminalPalette.MIN_CONTRAST - 1,
                        token + " at ratio " + ratio + " has contrast " + distance);
            }
        }
    }

    @Test
    @DisplayName("blending is deterministic and monotonic at the ends")
    void should_BlendDeterministically() {
        assertEquals(TerminalPalette.rgbFor(0.3f), TerminalPalette.rgbFor(0.3f));
        assertEquals(0xF2F0E8, TerminalPalette.rgbFor(-1f).get(Token.BOARD));
        assertEquals(0x161923, TerminalPalette.rgbFor(2f).get(Token.BOARD));
    }

    @Test
    @DisplayName("ANSI fallback picks the nearest of the 16 slots")
    void should_MapToNearestAnsiSlot() {
        assertEquals(TextColor.ANSI.BLACK, TerminalPalette.nearestAnsi(0x000000));
        assertEquals(TextColor.ANSI.WHITE_BRIGHT, TerminalPalette.nearestAnsi(0xFFFFFF));
        assertEquals(TextColor.ANSI.RED_BRIGHT, TerminalPalette.nearestAnsi(0xFF0000));
        assertEquals(TextColor.ANSI.GREEN_BRIGHT, TerminalPalette.nearestAnsi(0x00FF00));
    }

    @Test
    @DisplayName("truecolor returns RGB colours")
    void should_ReturnRgb_When_Truecolor() {
        TerminalPalette palette = new TerminalPalette(Depth.TRUECOLOR);

        assertTrue(palette.resolve(0f).color(Token.WATER) instanceof TextColor.RGB);
    }

    @Test
    @DisplayName("the ratio snaps to perceptual levels, with hysteresis beyond them")
    void should_StepRatio_When_BeyondTheBand() {
        float level = TerminalPalette.level(0.30f);
        assertEquals(level, TerminalPalette.band(0.30f, -1f), 1e-6);
        assertEquals(level, TerminalPalette.band(level, level), 1e-6);

        // las teclas recorren escalones contiguos
        float next = TerminalPalette.shiftLevel(level, 1);
        float previous = TerminalPalette.shiftLevel(level, -1);
        assertTrue(next > level);
        assertTrue(previous < level);
        assertEquals(level, TerminalPalette.shiftLevel(next, -1), 1e-6);
        assertEquals(next, TerminalPalette.band(next, level), 1e-6);
        assertEquals(previous, TerminalPalette.band(previous, level), 1e-6);

        // extremos deterministas
        assertEquals(0f, TerminalPalette.band(-1f, -1f), 1e-6);
        assertEquals(1f, TerminalPalette.band(2f, -1f), 1e-6);
    }

    @Test
    @DisplayName("levels are spaced by perceived lightness, not by raw ratio")
    void should_SpaceLevelsPerceptually() {
        java.util.List<Double> steps = new java.util.ArrayList<>();
        float ratio = 0f;
        double previous = perceived(TerminalPalette.rgbFor(ratio).get(TerminalPalette.Token.BOARD));
        for (int level = 0; level < TerminalPalette.BANDS; level++) {
            float next = TerminalPalette.shiftLevel(ratio, 1);
            assertTrue(next > ratio, "levels must advance: " + ratio + " -> " + next);
            ratio = next;
            double current =
                    perceived(TerminalPalette.rgbFor(ratio).get(TerminalPalette.Token.BOARD));
            steps.add(previous - current);
            previous = current;
        }
        double min = steps.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = steps.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        assertTrue(min > 5, "even the smallest step should be visible, was " + min);
        assertTrue(max / min < 1.6, "steps should be even, min=" + min + " max=" + max);
    }

    private static double perceived(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }

    @Test
    @DisplayName("256-colour indexing uses the grayscale ramp and reports are monotonic")
    void should_IndexWithGrayscaleRamp() {
        assertEquals(new TextColor.Indexed(255), TerminalPalette.nearestIndexed256(0xF2F0E8));
        assertEquals(new TextColor.Indexed(16), TerminalPalette.nearestIndexed256(0x000000));
        assertEquals(new TextColor.Indexed(231), TerminalPalette.nearestIndexed256(0xFFFFFF));

        int distinct = new java.util.HashSet<TextColor>().size();
        java.util.Set<TextColor> seen = new java.util.HashSet<>();
        for (int k = 0; k <= TerminalPalette.BANDS; k++) {
            float band = k / (float) TerminalPalette.BANDS;
            seen.add(TerminalPalette.nearestIndexed256(
                    TerminalPalette.rgbFor(band).get(TerminalPalette.Token.BOARD)));
        }
        distinct = seen.size();
        assertTrue(distinct >= 15,
                "the 256 palette should still show most levels, saw " + distinct);
    }
}
