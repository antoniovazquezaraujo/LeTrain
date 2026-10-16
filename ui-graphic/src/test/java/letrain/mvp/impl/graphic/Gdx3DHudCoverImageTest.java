package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
