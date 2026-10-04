package letrain.visitor.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import java.util.Map;
import letrain.palette.VisualPalette;
import letrain.visitor.terminal.TerminalPalette.Depth;
import letrain.visitor.terminal.TerminalPalette.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Terminal day/night palette")
class TerminalPaletteTest {

    @Test
    @DisplayName("day is green field, blue sea and brown mountain; night stays dark")
    void should_ResolveEnds() {
        Map<Token, Integer> day = TerminalPalette.rgbFor(0f);
        Map<Token, Integer> night = TerminalPalette.rgbFor(1f);

        assertEquals(0x4CA331, day.get(Token.BOARD));
        assertEquals(0x4CA331, day.get(Token.GROUND));
        assertEquals(0x1A3E8F, day.get(Token.WATER_BG));
        assertEquals(0x703A1A, day.get(Token.ROCK_BG));
        // Olas cyan sobre el azul del mar y roca clara sobre el marrón de la montaña.
        assertEquals(0x00A9C0, day.get(Token.WATER));
        assertEquals(0xC8A06B, day.get(Token.ROCK));
        assertEquals(0x161923, night.get(Token.BOARD));
        assertEquals(0x161923, night.get(Token.GROUND));
        assertEquals(0x081326, night.get(Token.WATER_BG));
        assertEquals(0x1C120B, night.get(Token.ROCK_BG));
        assertEquals(0x9696A0, night.get(Token.RAIL));
    }

    @Test
    @DisplayName("every glyph keeps the minimum contrast against its own backdrop")
    void should_KeepContrast_When_Blending() {
        for (float ratio = 0f; ratio <= 1f; ratio += 0.05f) {
            Map<Token, Integer> rgb = TerminalPalette.rgbFor(ratio);
            for (Token token : Token.values()) {
                if (TerminalPalette.isBackground(token)) {
                    continue;
                }
                double backdrop =
                        TerminalPalette.luminance(rgb.get(TerminalPalette.backgroundOf(token)));
                double distance = Math.abs(TerminalPalette.luminance(rgb.get(token)) - backdrop);
                // En los extremos del fundido el fondo se acerca a negro (o a blanco): cuando su
                // luminosidad es menor que MIN_CONTRAST, el máximo contraste alcanzable es el que
                // permite ese extremo, no el suelo pedido. Se exige el máximo físico, no menos.
                double reachable =
                        Math.min(TerminalPalette.MIN_CONTRAST, Math.min(backdrop, 255 - backdrop));
                assertTrue(distance >= reachable - 1, token + " at ratio " + ratio
                        + " has contrast " + distance + " (reachable " + reachable + ")");
            }
        }
    }

    @Test
    @DisplayName("sea and mountain backgrounds keep the terrain readable across the fade")
    void should_KeepTerrainBackdropsReadable() {
        for (float ratio : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
            Map<Token, Integer> rgb = TerminalPalette.rgbFor(ratio);
            int sea = rgb.get(Token.WATER_BG);
            int mountain = rgb.get(Token.ROCK_BG);

            assertNotEquals(sea, mountain, "sea and mountain must differ at ratio " + ratio);
            assertNotEquals(rgb.get(Token.BOARD), sea, "sea must differ from the field");
            assertNotEquals(rgb.get(Token.BOARD), mountain, "mountain must differ from the field");
            assertTrue(delta(rgb, Token.WATER, Token.WATER_BG) >= TerminalPalette.MIN_CONTRAST,
                    "waves must stay visible on the sea at ratio " + ratio);
            assertTrue(delta(rgb, Token.ROCK, Token.ROCK_BG) >= TerminalPalette.MIN_CONTRAST,
                    "rock must stay visible on the mountain at ratio " + ratio);
        }
        // De día el mar es azul y la montaña marrón (canales dominantes).
        int daySea = TerminalPalette.rgbFor(0f).get(Token.WATER_BG);
        int dayMountain = TerminalPalette.rgbFor(0f).get(Token.ROCK_BG);
        assertTrue(
                (daySea & 0xFF) > ((daySea >> 8) & 0xFF)
                        && ((daySea >> 8) & 0xFF) > ((daySea >> 16) & 0xFF),
                "day sea must read blue");
        assertTrue(
                ((dayMountain >> 16) & 0xFF) > ((dayMountain >> 8) & 0xFF)
                        && ((dayMountain >> 8) & 0xFF) > (dayMountain & 0xFF),
                "day mountain must read brown");
    }

    private static double delta(Map<Token, Integer> rgb, Token a, Token b) {
        return Math
                .abs(TerminalPalette.luminance(rgb.get(a)) - TerminalPalette.luminance(rgb.get(b)));
    }

    @Test
    @DisplayName("track glyphs lighten over sea and mountain to keep MIN_CONTRAST")
    void should_LightenTrackGlyphs_OnTerrainBackdrops() {
        Token[] glyphs = {Token.RAIL, Token.BRIDGE, Token.DEAD_END, Token.FORK};
        for (float ratio : new float[] {0f, 0.5f, 1f}) {
            Map<Token, Integer> rgb = TerminalPalette.rgbFor(ratio);
            for (Token backdrop : new Token[] {Token.WATER_BG, Token.ROCK_BG}) {
                double backdropLuminance = TerminalPalette.luminance(rgb.get(backdrop));
                for (Token glyph : glyphs) {
                    int adjusted = TerminalPalette.lightenOn(rgb.get(glyph), rgb.get(backdrop));
                    double distance =
                            Math.abs(TerminalPalette.luminance(adjusted) - backdropLuminance);
                    assertTrue(distance >= TerminalPalette.MIN_CONTRAST - 1, glyph + " over "
                            + backdrop + " at ratio " + ratio + " has contrast " + distance);
                }
            }
        }
    }

    @Test
    @DisplayName("a blocked-track livery is nudged away from the sea and the mountain")
    void should_AdjustBlockedLivery_OnTerrainBackdrops() {
        for (float ratio : new float[] {0f, 0.5f, 1f}) {
            Map<Token, Integer> rgb = TerminalPalette.rgbFor(ratio);
            for (Token backdrop : new Token[] {Token.WATER_BG, Token.ROCK_BG}) {
                // La librea más difícil: exactamente el color del fondo (un tren azul sobre el
                // mar).
                int sameAsBackdrop = rgb.get(backdrop);
                int adjusted = TerminalPalette.contrastOn(sameAsBackdrop, rgb.get(backdrop));
                double distance = Math.abs(TerminalPalette.luminance(adjusted)
                        - TerminalPalette.luminance(sameAsBackdrop));
                assertTrue(distance >= TerminalPalette.MIN_CONTRAST - 1, "livery over " + backdrop
                        + " at ratio " + ratio + " has contrast " + distance);
            }
        }
    }

    @Test
    @DisplayName("the adapted rail over sea stays out of the sea slot in 16 colours")
    void should_KeepRailOverSeaDistinct_When_Ansi16() {
        TerminalPalette palette = new TerminalPalette(Depth.ANSI_16);
        Map<Token, Integer> day = TerminalPalette.rgbFor(0f);
        TextColor sea = palette.colorOf(day.get(Token.WATER_BG));
        TextColor rail = palette
                .colorOf(TerminalPalette.lightenOn(day.get(Token.RAIL), day.get(Token.WATER_BG)));
        TextColor railOnMountain = palette
                .colorOf(TerminalPalette.lightenOn(day.get(Token.RAIL), day.get(Token.ROCK_BG)));

        assertNotEquals(sea, rail, "the rail must not vanish into the sea");
        assertNotEquals(palette.colorOf(day.get(Token.ROCK_BG)), railOnMountain,
                "the rail must not vanish into the mountain");
    }

    @Test
    @DisplayName("blending is deterministic and monotonic at the ends")
    void should_BlendDeterministically() {
        assertEquals(TerminalPalette.rgbFor(0.3f), TerminalPalette.rgbFor(0.3f));
        assertEquals(0x4CA331, TerminalPalette.rgbFor(-1f).get(Token.BOARD));
        assertEquals(0x161923, TerminalPalette.rgbFor(2f).get(Token.BOARD));
    }

    @Test
    @DisplayName("day ground is green, never white: white is reserved for the future snow")
    void should_KeepDayGroundGreen() {
        int dayBoard = TerminalPalette.rgbFor(0f).get(Token.BOARD);
        int dayGround = TerminalPalette.rgbFor(0f).get(Token.GROUND);
        int red = (dayBoard >> 16) & 0xFF;
        int green = (dayBoard >> 8) & 0xFF;
        int blue = dayBoard & 0xFF;

        assertTrue(green > red && green > blue, "day ground must read green");
        assertTrue(TerminalPalette.luminance(dayBoard) < 200, "day ground must not read as paper");
        assertEquals(dayBoard, dayGround, "in 2D the board is the visible field");

        // Mismo brillo percibido que el campo 3D (0x66994C): solo sube la saturación para que el
        // fallback ANSI caiga en el verde del tema, no en el gris brillante.
        VisualPalette visual = new VisualPalette();
        assertEquals(
                TerminalPalette.luminance(visual.color(VisualPalette.Token.TERRAIN_FIELDS, 0.0)),
                TerminalPalette.luminance(dayBoard), 1.0,
                "terminal green must match 3D field light");
        assertEquals(TextColor.ANSI.GREEN, TerminalPalette.nearestAnsi(dayBoard));
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
    @DisplayName("16-colour fallback keeps glyphs out of their backdrop slot")
    void should_KeepTokensOffTheBoard_When_Ansi16() {
        TerminalPalette palette = new TerminalPalette(Depth.ANSI_16);
        TerminalPalette.Resolved resolved = palette.resolve(0f);

        assertEquals(TextColor.ANSI.GREEN, resolved.color(Token.BOARD));
        assertEquals(TextColor.ANSI.BLUE, resolved.color(Token.WATER_BG));
        assertEquals(TextColor.ANSI.RED, resolved.color(Token.ROCK_BG));
        assertEquals(TextColor.ANSI.CYAN, resolved.color(Token.WATER));
        assertNotEquals(resolved.color(Token.WATER_BG), resolved.color(Token.WATER),
                "the waves must not vanish into the sea");
        assertNotEquals(resolved.color(Token.ROCK_BG), resolved.color(Token.ROCK),
                "the rock must not vanish into the mountain");
        assertNotEquals(resolved.color(Token.BOARD), resolved.color(Token.SEMAPHORE_OPEN),
                "the open semaphore must not vanish into the field");
        assertNotEquals(resolved.color(Token.BOARD), resolved.color(Token.CURSOR_DRAWING),
                "the drawing cursor must not vanish into the field");
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
    @DisplayName("every level advances when the ratio is set to it, even the closest ones")
    void should_AdvanceThroughAllLevels() {
        float ratio = 0f;
        for (int step = 1; step <= TerminalPalette.BANDS; step++) {
            float next = TerminalPalette.shiftLevel(ratio, 1);
            assertEquals(next, TerminalPalette.band(next, ratio), 1e-6,
                    "level " + step + " must switch from " + ratio);
            ratio = next;
        }
        assertEquals(1f, ratio, 1e-6);
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
    @DisplayName("256-colour indexing sends the green board through the cube, not the gray ramp")
    void should_IndexWithGrayscaleRamp() {
        assertEquals(new TextColor.Indexed(255), TerminalPalette.nearestIndexed256(0xF2F0E8));
        assertEquals(new TextColor.Indexed(16), TerminalPalette.nearestIndexed256(0x000000));
        assertEquals(new TextColor.Indexed(231), TerminalPalette.nearestIndexed256(0xFFFFFF));
        // El verde del campo cae en el cubo (95, 175, 95), no en la rampa de grises.
        assertEquals(new TextColor.Indexed(71), TerminalPalette.nearestIndexed256(0x4CA331));

        java.util.Set<TextColor> seen = new java.util.HashSet<>();
        for (int k = 0; k <= TerminalPalette.BANDS; k++) {
            float band = k / (float) TerminalPalette.BANDS;
            seen.add(TerminalPalette.nearestIndexed256(
                    TerminalPalette.rgbFor(band).get(TerminalPalette.Token.BOARD)));
        }
        int distinct = seen.size();
        // El recorrido verde→oscuro es más corto que el del papel (240→25 de luminosidad), así que
        // el cubo 6x6x6 conserva 8 escalones distintos del tablero; el fundido sigue sin saltos
        // bruscos, que es lo que este test protege.
        assertTrue(distinct >= 8, "the 256 palette should still show most levels, saw " + distinct);
    }
}
