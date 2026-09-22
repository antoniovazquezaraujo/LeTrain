package letrain.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Visual palette")
class VisualPaletteTest {

    private final VisualPalette palette = new VisualPalette();

    @Test
    @DisplayName("keys are returned at 0, 0.5 and 1")
    void should_ReturnKeys() {
        int day = palette.color(VisualPalette.Token.AMBIENT_LIGHT, 0.0);
        int dusk = palette.color(VisualPalette.Token.AMBIENT_LIGHT, 0.5);
        int night = palette.color(VisualPalette.Token.AMBIENT_LIGHT, 1.0);

        assertEquals(0x8C8C85, day);
        assertEquals(0x594738, dusk);
        assertEquals(0x1F2438, night);
    }

    @Test
    @DisplayName("ratios outside 0..1 are clamped")
    void should_ClampRatio() {
        assertEquals(0x8C8C85, palette.color(VisualPalette.Token.AMBIENT_LIGHT, -3.0));
        assertEquals(0x1F2438, palette.color(VisualPalette.Token.AMBIENT_LIGHT, 9.0));
    }

    @Test
    @DisplayName("the ambient light darkens through the night")
    void should_DarkenAmbientLight() {
        double previous = Double.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            double luminance =
                    luminance(palette.color(VisualPalette.Token.AMBIENT_LIGHT, i / 20.0));
            assertTrue(luminance <= previous, "ratio=" + i / 20.0);
            previous = luminance;
        }
    }

    @Test
    @DisplayName("terrain tokens have their day, dusk and night keys (phase 1b)")
    void should_ReturnTerrainKeys() {
        assertEquals(0x66994C, palette.color(VisualPalette.Token.TERRAIN_FIELDS, 0.0));
        assertEquals(0x526B38, palette.color(VisualPalette.Token.TERRAIN_FIELDS, 0.5));
        assertEquals(0x1F2E24, palette.color(VisualPalette.Token.TERRAIN_FIELDS, 1.0));
        assertEquals(0x3366CC, palette.color(VisualPalette.Token.TERRAIN_WATER, 0.0));
        assertEquals(0x0F1F4D, palette.color(VisualPalette.Token.TERRAIN_WATER, 1.0));
        assertEquals(0x80664D, palette.color(VisualPalette.Token.TERRAIN_MOUNTAIN, 0.0));
        assertEquals(0xBFBFBF, palette.color(VisualPalette.Token.TABLE_GRID, 0.0));
        assertEquals(0x218C21, palette.color(VisualPalette.Token.DECOR_BOX, 0.0));
    }

    @Test
    @DisplayName("every terrain token darkens monotonically through the night")
    void should_DarkenTerrain() {
        VisualPalette.Token[] terrain = {VisualPalette.Token.TERRAIN_FIELDS,
                VisualPalette.Token.TERRAIN_WATER, VisualPalette.Token.TERRAIN_MOUNTAIN,
                VisualPalette.Token.TERRAIN_BALLAST, VisualPalette.Token.STRUCTURE_BRIDGE_PILLAR,
                VisualPalette.Token.STRUCTURE_TUNNEL_PORTAL,
                VisualPalette.Token.STRUCTURE_TERRAIN_WALL, VisualPalette.Token.TABLE_GRID,
                VisualPalette.Token.DECOR_BOX};
        for (VisualPalette.Token token : terrain) {
            double previous = Double.MAX_VALUE;
            for (int i = 0; i <= 20; i++) {
                double luminance = luminance(palette.color(token, i / 20.0));
                assertTrue(luminance <= previous, token + " at ratio " + i / 20.0);
                previous = luminance;
            }
        }
    }

    @Test
    @DisplayName("interpolation is perceptual (linear light)")
    void should_MixPerceptually() {
        assertEquals(0x000000, VisualPalette.mix(0x000000, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, VisualPalette.mix(0x000000, 0xFFFFFF, 1f));
        int mid = VisualPalette.mix(0x000000, 0xFFFFFF, 0.5f);
        assertTrue(mid > 0xAAAAAA && mid < 0xCCCCCC, "mid=" + Integer.toHexString(mid));
    }

    private static double luminance(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }
}
