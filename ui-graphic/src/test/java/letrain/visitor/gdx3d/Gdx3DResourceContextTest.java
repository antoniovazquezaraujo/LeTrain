package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gdx3D resource context palette helpers")
class Gdx3DResourceContextTest {

    @Test
    @DisplayName("setIfChanged updates a diffuse colour from 0xRRGGBB")
    void should_SetColour_FromRgb() {
        Color color = new Color(0f, 0f, 0f, 1f);

        Gdx3DResourceContext.setIfChanged(color, 0x66994C);

        assertEquals(0x66 / 255f, color.r, 1e-6);
        assertEquals(0x99 / 255f, color.g, 1e-6);
        assertEquals(0x4C / 255f, color.b, 1e-6);
        assertEquals(1f, color.a, 1e-6);
    }

    @Test
    @DisplayName("setIfChanged keeps the colour when it already matches")
    void should_KeepColour_When_Unchanged() {
        Color color = new Color(0x66 / 255f, 0x99 / 255f, 0x4C / 255f, 1f);

        Gdx3DResourceContext.setIfChanged(color, 0x66994C);

        assertEquals(0x66 / 255f, color.r, 1e-6);
        assertEquals(0x99 / 255f, color.g, 1e-6);
        assertEquals(0x4C / 255f, color.b, 1e-6);
    }

    @Test
    @DisplayName("attenuating a player colour never mutates the original constant")
    void should_NotMutatePlayerColour_When_Attenuating() {
        Color yellow = new Color(Color.YELLOW);

        Color dimmed = Gdx3DResourceContext.attenuate(Color.YELLOW, 0.55f);

        assertEquals(1f, Color.YELLOW.r, 1e-6);
        assertEquals(1f, Color.YELLOW.g, 1e-6);
        assertEquals(0f, Color.YELLOW.b, 1e-6);
        assertEquals(0.55f, dimmed.r, 1e-6);
        assertEquals(0.55f, dimmed.g, 1e-6);
        assertEquals(0f, dimmed.b, 1e-6);
        assertEquals(1f, yellow.r, 1e-6);

        // repeated attenuation does not compound on the source
        Gdx3DResourceContext.attenuate(Color.YELLOW, 0.55f);
        assertEquals(1f, Color.YELLOW.r, 1e-6);
    }
}
