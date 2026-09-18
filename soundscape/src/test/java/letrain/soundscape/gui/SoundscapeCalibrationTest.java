package letrain.soundscape.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import letrain.soundscape.SoundGate;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape GUI calibration")
class SoundscapeCalibrationTest {

    private static final String STYLE = "/styles/valle-norte.sound";

    private final TextStyleLoader loader = new TextStyleLoader();

    private SoundscapePlayer calibratedPlayer() throws IOException {
        List<String> lines = loader.readResourceLines(STYLE);
        SoundscapePlayer player = new SoundscapePlayer();
        player.initStyle(loader.parse(lines), "valle-norte.sound", lines);
        player.buildControlsScroll();
        player.buildResultsPanel();
        player.rebuildForStyle();
        return player;
    }

    @Test
    @DisplayName("every sound gets a gain slider and a VU meter")
    void should_CreateControls_ForEverySound() throws IOException {
        SoundscapePlayer player = calibratedPlayer();

        assertTrue(player.gainSliders().containsKey("cicadas"));
        assertTrue(player.gainSliders().containsKey("weather-rain"));
        assertTrue(player.gainSliders().containsKey("height-wind"));
        assertEquals(15, player.gainSliders().size());
        assertEquals(player.gainSliders().keySet(), player.volumeBars().keySet());
    }

    @Test
    @DisplayName("gain sliders and VU meters are aligned in columns")
    void should_AlignCalibrationColumns() throws IOException {
        SoundscapePlayer player = calibratedPlayer();
        player.zoneSliders().get("fields").setValue(100); // populate the meters for the render
        layoutPanel(player.resultsPanel());
        renderForInspection(player.resultsPanel());

        assertAligned(player.volumeBars().values());
        int x = player.gainSliders().values().iterator().next().getX();
        for (JSlider slider : player.gainSliders().values()) {
            assertEquals(x, slider.getX(), "every gain slider should start at the same x");
        }
    }

    @Test
    @DisplayName("moving a gain slider moves its VU meter")
    void should_MoveMeter_When_GainSliderChanges() throws IOException {
        SoundscapePlayer player = calibratedPlayer();
        player.zoneSliders().get("fields").setValue(100);

        JProgressBar meter = player.volumeBars().get("cicadas");
        int full = meter.getValue();
        assertTrue(full > 0, "cicadas should sound at noon with fields active");

        player.gainSliders().get("cicadas").setValue(50);
        assertEquals(full / 2, meter.getValue(), 1);

        player.gainSliders().get("cicadas").setValue(0);
        assertEquals(0, meter.getValue());
    }

    @Test
    @DisplayName("moving a slider calibrates the style and the exported text")
    void should_ExportCalibration_When_GainSliderMoves() throws IOException {
        SoundscapePlayer player = calibratedPlayer();

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
    @DisplayName("distance sliders write [distance-by-sound] and parse back")
    void should_ExportDistanceSensitivity_When_SliderMoves() throws IOException {
        SoundscapePlayer player = calibratedPlayer();

        player.distanceSliders().get("hawks").setValue(30);

        SoundscapeStyle reloaded = loader.parse(player.exportLines());
        assertEquals(0.3f, reloaded.distanceSensitivityOf("hawks"), 1e-6);
        assertEquals(1f, reloaded.distanceSensitivityOf("cicadas"), 1e-6);
    }

    @Test
    @DisplayName("gate sliders write [silence-when] and can switch a gate off")
    void should_ExportSilenceGates_When_GateSlidersMove() throws IOException {
        SoundscapePlayer player = calibratedPlayer();

        JSlider rainGate = player.gateSliders().get("crickets").get("rain");
        assertEquals(25, rainGate.getValue(), "bundled style declares crickets rain > 0.25");
        rainGate.setValue(40);
        assertEquals(List.of(new SoundGate("rain", 0.4f)),
                loader.parse(player.exportLines()).gatesOf("crickets"));

        rainGate.setValue(100);
        assertTrue(loader.parse(player.exportLines()).gatesOf("crickets").isEmpty());
    }

    @Test
    @DisplayName("returning a slider to 100% removes its gain from the export")
    void should_DropGain_When_SliderReturnsToDefault() throws IOException {
        SoundscapePlayer player = calibratedPlayer();
        JSlider slider = player.gainSliders().get("cicadas");

        slider.setValue(40);
        slider.setValue(100);

        assertEquals(1f, loader.parse(player.exportLines()).gainOf("cicadas"), 1e-6);
        assertFalse(player.exportLines().contains("[gains]"));
    }

    private void layoutPanel(JPanel panel) {
        panel.setSize(760, 520);
        layoutTree(panel);
    }

    /** Writes a PNG of the calibration panel when -Dsoundscape.renderLayout=true. */
    private void renderForInspection(JPanel panel) throws IOException {
        if (!Boolean.getBoolean("soundscape.renderLayout")) {
            return;
        }
        BufferedImage image = new BufferedImage(panel.getWidth() + 20, panel.getHeight() + 20,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File("/tmp/opencode/gui-gains.png"));
    }

    private void layoutTree(Component component) {
        if (component instanceof Container container) {
            container.invalidate();
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private void assertAligned(Collection<JProgressBar> bars) {
        int x = -1;
        int width = -1;
        for (JProgressBar bar : bars) {
            if (x < 0) {
                x = bar.getX();
                width = bar.getWidth();
            }
            assertEquals(x, bar.getX(), "every meter should start at the same x");
            assertEquals(width, bar.getWidth(), "every meter should have the same width");
            assertTrue(width > 50, "meters should fill the panel, was " + width);
        }
    }
}
