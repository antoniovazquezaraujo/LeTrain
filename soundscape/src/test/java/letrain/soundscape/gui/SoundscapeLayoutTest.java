package letrain.soundscape.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JViewport;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape GUI layout")
class SoundscapeLayoutTest {

    private static final int SCROLL_WIDTH = 360;
    private static final int SCROLL_HEIGHT = 600;

    @Test
    @DisplayName("all zone sliders start at the same x and have the same width")
    void should_AlignZoneSliders() throws Exception {
        SoundscapeStyle style = new TextStyleLoader().loadResource("/styles/valle-norte.sound");
        SoundscapePlayer player = new SoundscapePlayer();
        player.initStyle(style, "valle-norte.sound");
        JScrollPane scroll = player.buildControlsScroll();
        player.rebuildForStyle();

        layoutScroll(scroll);
        renderForInspection(scroll);

        assertFalse(player.zoneSliders().isEmpty());
        int x = -1;
        int width = -1;
        for (JSlider slider : player.zoneSliders().values()) {
            if (x < 0) {
                x = slider.getX();
                width = slider.getWidth();
            }
            assertEquals(x, slider.getX(), "every slider should start at the same x");
            assertEquals(width, slider.getWidth(), "every slider should have the same width");
            assertTrue(width > 100, "sliders should fill the column, was " + width);
        }
    }

    /**
     * Lays out the scroll pane the way JViewport would: the view keeps its preferred height when it
     * is taller than the viewport (ScrollablePanel does not track the viewport height), which also
     * keeps GridBagLayout in preferred-size mode instead of collapsing rows to their minimum size.
     */
    private void layoutScroll(JScrollPane scroll) {
        scroll.setSize(SCROLL_WIDTH, SCROLL_HEIGHT);
        scroll.doLayout();
        JViewport viewport = scroll.getViewport();
        Component view = viewport.getView();
        Dimension extent = viewport.getExtentSize();
        Dimension preferred = view.getPreferredSize();
        view.setSize(extent.width, Math.max(extent.height, preferred.height));
        layoutTree(view);
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

    /** Writes a PNG for manual inspection when -Dsoundscape.renderLayout=true. */
    private void renderForInspection(JScrollPane scroll) throws Exception {
        if (!Boolean.getBoolean("soundscape.renderLayout")) {
            return;
        }
        BufferedImage image = new BufferedImage(SCROLL_WIDTH + 40, SCROLL_HEIGHT + 60,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        scroll.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File("/tmp/opencode/gui-controls.png"));
    }
}
