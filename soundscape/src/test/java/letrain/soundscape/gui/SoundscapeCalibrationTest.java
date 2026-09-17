package letrain.soundscape.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import javax.swing.JSlider;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape GUI calibration")
class SoundscapeCalibrationTest {

    private static final String STYLE = "/styles/valle-norte.sound";

    private final TextStyleLoader loader = new TextStyleLoader();

    private SoundscapePlayer playerWithStyle() throws IOException {
        List<String> lines = loader.readResourceLines(STYLE);
        SoundscapePlayer player = new SoundscapePlayer();
        player.initStyle(loader.parse(lines), "valle-norte.sound", lines);
        player.buildControlsScroll();
        player.rebuildForStyle();
        return player;
    }

    @Test
    @DisplayName("gain sliders cover catalog, weather and height sounds")
    void should_CreateGainSlider_ForEverySound() throws IOException {
        SoundscapePlayer player = playerWithStyle();

        assertTrue(player.gainSliders().containsKey("cicadas"));
        assertTrue(player.gainSliders().containsKey("weather-rain"));
        assertTrue(player.gainSliders().containsKey("height-wind"));
        assertEquals(16, player.gainSliders().size());
    }

    @Test
    @DisplayName("moving a slider calibrates the style and the exported text")
    void should_ExportCalibration_When_GainSliderMoves() throws IOException {
        SoundscapePlayer player = playerWithStyle();

        player.gainSliders().get("cicadas").setValue(40);

        List<String> exported = player.exportLines();
        SoundscapeStyle reloaded = loader.parse(exported);
        assertEquals(0.4f, reloaded.gainOf("cicadas"), 1e-6);
        assertEquals(1f, reloaded.gainOf("dogs"), 1e-6);
        assertTrue(exported.stream()
                .anyMatch(line -> line.replaceAll("\\s+", " ").trim().equals("cicadas = 0.4")));
        assertTrue(exported
                .contains("# ============================================================"));
    }

    @Test
    @DisplayName("returning a slider to 100% removes its gain from the export")
    void should_DropGain_When_SliderReturnsToDefault() throws IOException {
        SoundscapePlayer player = playerWithStyle();
        JSlider slider = player.gainSliders().get("cicadas");

        slider.setValue(40);
        slider.setValue(100);

        assertEquals(1f, loader.parse(player.exportLines()).gainOf("cicadas"), 1e-6);
        assertFalse(player.exportLines().contains("[gains]"));
    }
}
