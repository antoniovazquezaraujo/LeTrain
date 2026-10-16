package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #775: the 3D title cover paints the letter-train capture full screen in {@code cover} mode.
 * The scaling and centring maths are pure and pinned here without a GL context; the 833x539 capture
 * bundled with the game is the reference aspect ratio.
 */
@DisplayName("3D cover image: cover-mode geometry")
class Gdx3DHudCoverImageTest {

    private static final int IMG_W = 833;
    private static final int IMG_H = 539;
    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("a 16:9 screen is filled on width and overflows on height")
    void should_FillWidth_When_ScreenIsWider() {
        float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, 1920, 1080);

        assertEquals(1920f / IMG_W, scale, EPSILON);
        assertTrue(IMG_W * scale >= 1920f - 0.5f, "the width must reach the screen edge");
        assertTrue(IMG_H * scale >= 1080f, "the height must not leave a letterbox bar");
    }

    @Test
    @DisplayName("a screen taller than the capture is filled on height")
    void should_FillHeight_When_ScreenIsTaller() {
        float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, 1000, 2000);

        assertEquals(2000f / IMG_H, scale, EPSILON);
        assertTrue(IMG_H * scale >= 2000f, "the height must reach the screen edge");
    }

    @Test
    @DisplayName("a very wide screen is still filled on width")
    void should_FillWidth_When_ScreenIsVeryWide() {
        float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, 3000, 1000);

        assertEquals(3000f / IMG_W, scale, EPSILON);
    }

    @Test
    @DisplayName("an exact match needs no scaling")
    void should_ReturnOne_When_SizesMatch() {
        assertEquals(1f, Gdx3DHud.coverScale(IMG_W, IMG_H, IMG_W, IMG_H), EPSILON);
    }

    @Test
    @DisplayName("a degenerate size falls back to 1 without NaN or division by zero")
    void should_ReturnOne_When_SizeIsDegenerate() {
        assertEquals(1f, Gdx3DHud.coverScale(IMG_W, IMG_H, 0, 1080), EPSILON);
        assertEquals(1f, Gdx3DHud.coverScale(IMG_W, IMG_H, 1920, 0), EPSILON);
        assertEquals(1f, Gdx3DHud.coverScale(0, IMG_H, 1920, 1080), EPSILON);
        assertEquals(1f, Gdx3DHud.coverScale(IMG_W, 0, 1920, 1080), EPSILON);
        assertEquals(1f, Gdx3DHud.coverScale(-1, -1, -1, -1), EPSILON);
        assertFalse(Float.isNaN(Gdx3DHud.coverScale(IMG_W, IMG_H, 0, 0)));
    }

    @Test
    @DisplayName("the centring offset is negative or zero, never a bar")
    void should_ReturnNonPositiveOffset() {
        float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, 1920, 1080);

        assertTrue(Gdx3DHud.coverOffset(IMG_W * scale, 1920) <= EPSILON,
                "no bar on the filled axis");
        assertTrue(Gdx3DHud.coverOffset(IMG_H * scale, 1080) <= EPSILON,
                "no bar on the other axis");
        assertEquals(0f, Gdx3DHud.coverOffset(1920f, 1920), EPSILON);
    }

    @Test
    @DisplayName("every positive screen is covered on both axes, without letterboxing")
    void should_CoverBothAxes_When_ScreenIsPositive() {
        int[][] screens = {{1, 1}, {100000, 1}, {1, 100000}, {833, 539}, {1920, 1080}, {1000, 2000},
                {3000, 1000}, {1280, 720}, {720, 1280}, {2, 3}};

        for (int[] screen : screens) {
            int screenW = screen[0];
            int screenH = screen[1];

            float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, screenW, screenH);

            assertTrue(IMG_W * scale >= screenW - 0.5f,
                    "the width must reach the screen edge for " + screenW + "x" + screenH);
            assertTrue(IMG_H * scale >= screenH - 0.5f,
                    "the height must not leave a bar for " + screenW + "x" + screenH);
        }
    }

    @Test
    @DisplayName("the offsets split the overflow evenly, centring the picture on both axes")
    void should_CentreSymmetrically_When_ScreenIsPositive() {
        int[][] screens = {{100000, 1}, {1, 100000}, {1920, 1080}, {1000, 2000}, {833, 539}};

        for (int[] screen : screens) {
            int screenW = screen[0];
            int screenH = screen[1];
            float scale = Gdx3DHud.coverScale(IMG_W, IMG_H, screenW, screenH);

            float offX = Gdx3DHud.coverOffset(IMG_W * scale, screenW);
            float offY = Gdx3DHud.coverOffset(IMG_H * scale, screenH);

            assertEquals((screenW - IMG_W * scale) / 2f, offX, EPSILON,
                    "offX must be exactly half of the horizontal overflow");
            assertEquals((screenH - IMG_H * scale) / 2f, offY, EPSILON,
                    "offY must be exactly half of the vertical overflow");
            assertTrue(offX <= EPSILON && offY <= EPSILON,
                    "no bar on either axis for " + screenW + "x" + screenH);
        }
    }

    @Test
    @DisplayName("a sweep of odd image and screen sizes never yields NaN, infinity or division by zero")
    void should_NeverYieldNaNOrInfinity_When_SweepingOddSizes() {
        int[] sizes = {1, 2, 3, 100, 539, 833, 1000, 1920, 100000};

        for (int imageW : sizes) {
            for (int imageH : sizes) {
                for (int screenW : sizes) {
                    for (int screenH : sizes) {
                        float scale = Gdx3DHud.coverScale(imageW, imageH, screenW, screenH);
                        assertTrue(Float.isFinite(scale) && scale > 0f,
                                "scale must be finite and positive: img " + imageW + "x" + imageH
                                        + " screen " + screenW + "x" + screenH);

                        float offX = Gdx3DHud.coverOffset(imageW * scale, screenW);
                        float offY = Gdx3DHud.coverOffset(imageH * scale, screenH);
                        assertTrue(Float.isFinite(offX) && Float.isFinite(offY),
                                "offsets must be finite: img " + imageW + "x" + imageH + " screen "
                                        + screenW + "x" + screenH);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a 1x1 capture blows up to cover the screen without NaN")
    void should_CoverScreen_When_CaptureIsOnePixel() {
        float scale = Gdx3DHud.coverScale(1, 1, 1920, 1080);

        assertEquals(1920f, scale, EPSILON, "the larger axis ratio wins");
        assertTrue(1 * scale >= 1920f - EPSILON, "the width must reach the screen edge");
        assertTrue(1 * scale >= 1080f - EPSILON, "the height must not leave a bar");
    }

    /**
     * Regression guard for #775: the 3D cover paints the letter-train capture and shows no text, so
     * the HUD must not reach for the 2D {@code CoverText.TITLE}/{@code PROMPT} anymore. Checked on
     * the source because the reference would live inside {@code showCover()}, which needs a GL
     * context to run. Word-boundary matching keeps the {@code openCoverTexture} helper from being
     * mistaken for a {@code CoverText} reference.
     */
    @Test
    @DisplayName("the 3D HUD source no longer references the 2D CoverText")
    void should_NotReferenceCoverText() throws IOException {
        Path source = Path.of("src/main/java/letrain/mvp/impl/graphic/Gdx3DHud.java");
        Assumptions.assumeTrue(Files.exists(source),
                "Gdx3DHud.java not reachable from the test working directory");

        String text = Files.readString(source);

        assertFalse(Pattern.compile("\\bCoverText\\b").matcher(text).find(),
                "Gdx3DHud must not reference CoverText: the 3D cover shows no text");
    }
}
