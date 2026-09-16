package letrain.soundscape.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import letrain.soundscape.Composition;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.StyleLoader;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;

/**
 * Simple Swing playground for a style file: move time, zone weights, height and weather and watch
 * the target mix change live. No audio yet; it prints the same numbers the engine feeds the mixer.
 */
public final class SoundscapePlayer {

    private static final String DEFAULT_STYLE = "/styles/valle-norte.sound";
    private static final String CUSTOM = "(custom)";
    private static final float MIN_VOLUME = 0.005f;

    private final StyleLoader loader = new TextStyleLoader();
    private final SoundscapeEngine engine = new SoundscapeEngineImpl();

    private SoundscapeStyle style;
    private PlayerState state;
    private String styleName = "valle-norte.sound";

    private JFrame frame;
    private JLabel timeLabel;
    private JLabel statusLabel;
    private JLabel styleLabel;
    private JSlider timeSlider;
    private JSlider heightSlider;
    private final Map<String, JSlider> weatherSliders = new LinkedHashMap<>();
    private JComboBox<String> weatherCombo;
    private JPanel zonesPanel;
    private JPanel resultsPanel;
    private final Map<String, JSlider> zoneSliders = new LinkedHashMap<>();

    private boolean updating;
    private Timer playTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SoundscapePlayer().show(args));
    }

    void show(String[] args) {
        try {
            if (args.length > 0) {
                Path path = Path.of(args[0]);
                style = loader.load(path);
                styleName = path.getFileName().toString();
            } else {
                style = loader.loadResource(DEFAULT_STYLE);
                styleName = "valle-norte.sound";
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "No se pudo cargar el estilo: " + e.getMessage(),
                    "soundscape", JOptionPane.ERROR_MESSAGE);
            return;
        }
        state = new PlayerState(style);
        buildFrame();
        rebuildForStyle();
        updateComposition();
        frame.setVisible(true);
    }

    private void buildFrame() {
        frame = new JFrame("Soundscape test player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        styleLabel = new JLabel();
        header.add(styleLabel, BorderLayout.WEST);
        JButton loadButton = new JButton("Cargar estilo…");
        loadButton.addActionListener(e -> chooseStyle());
        header.add(loadButton, BorderLayout.EAST);
        controls.add(header);

        controls.add(section("Tiempo"));
        timeLabel = new JLabel();
        controls.add(timeLabel);
        timeSlider = new JSlider(0, 24 * 60 - 1, state.minuteOfDay());
        timeSlider.addChangeListener(e -> {
            if (!updating) {
                state.setMinuteOfDay(timeSlider.getValue());
                updateComposition();
            }
        });
        controls.add(timeSlider);
        JButton playButton = new JButton("▶ Día completo");
        playButton.addActionListener(e -> togglePlay(playButton));
        controls.add(playButton);

        controls.add(section("Altura (zoom)"));
        heightSlider = slider(100, 0);
        controls.add(heightSlider);

        controls.add(section("Clima"));
        weatherCombo = new JComboBox<>();
        weatherCombo.addActionListener(e -> {
            if (updating) {
                return;
            }
            String name = (String) weatherCombo.getSelectedItem();
            if (name == null || CUSTOM.equals(name)) {
                return;
            }
            state.applyPreset(style.climatePresets().get(name));
            syncWeatherSliders();
            updateComposition();
        });
        controls.add(weatherCombo);
        controls.add(weatherSliderRow("rain"));
        controls.add(weatherSliderRow("wind"));
        controls.add(weatherSliderRow("storm"));

        controls.add(section("Zonas"));
        zonesPanel = new JPanel();
        zonesPanel.setLayout(new BoxLayout(zonesPanel, BoxLayout.Y_AXIS));
        controls.add(zonesPanel);

        JScrollPane scroll = new JScrollPane(controls);
        scroll.setPreferredSize(new Dimension(340, 600));
        frame.add(scroll, BorderLayout.WEST);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane resultsScroll = new JScrollPane(resultsPanel);
        frame.add(resultsScroll, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setSize(900, 640);
        frame.setLocationRelativeTo(null);
    }

    private JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
        return label;
    }

    private JSlider slider(int max, int value) {
        JSlider slider = new JSlider(0, max, value);
        slider.addChangeListener(e -> {
            if (!updating) {
                onSliderChanged(slider);
            }
        });
        return slider;
    }

    private JPanel weatherSliderRow(String key) {
        JSlider slider = slider(100, 0);
        weatherSliders.put(key, slider);
        JLabel label = new JLabel(key);
        label.setPreferredSize(new Dimension(60, 18));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private void onSliderChanged(JSlider source) {
        if (source == heightSlider) {
            state.setHeight(source.getValue() / 100f);
        } else if (weatherSliders.containsValue(source)) {
            state.setWeather(valueOf("rain"), valueOf("wind"), valueOf("storm"));
            markWeatherCustom();
        } else {
            for (Map.Entry<String, JSlider> zone : zoneSliders.entrySet()) {
                if (zone.getValue() == source) {
                    state.setZoneWeight(zone.getKey(), source.getValue() / 100f);
                }
            }
        }
        updateComposition();
    }

    private float valueOf(String weather) {
        JSlider slider = weatherSliders.get(weather);
        return slider == null ? 0f : slider.getValue() / 100f;
    }

    private void markWeatherCustom() {
        if (!CUSTOM.equals(weatherCombo.getSelectedItem())) {
            updating = true;
            weatherCombo.setSelectedItem(CUSTOM);
            updating = false;
        }
    }

    private void togglePlay(JButton button) {
        if (playTimer != null && playTimer.isRunning()) {
            playTimer.stop();
            button.setText("▶ Día completo");
            return;
        }
        button.setText("⏸ Pausa");
        playTimer = new Timer(50, e -> {
            state.advance(5);
            syncTimeSlider();
            updateComposition();
        });
        playTimer.start();
    }

    private void chooseStyle() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Soundscape style (*.sound)", "sound"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            style = loader.load(file.toPath());
            styleName = file.getName();
            state = new PlayerState(style);
            rebuildForStyle();
            updateComposition();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "No se pudo cargar: " + e.getMessage(),
                    "soundscape", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void rebuildForStyle() {
        updating = true;
        styleLabel.setText(styleName);
        zoneSliders.clear();
        zonesPanel.removeAll();
        for (String zone : style.zones().keySet()) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.add(new JLabel(zone), BorderLayout.WEST);
            JSlider slider =
                    slider(100, Math.round(state.zoneWeights().getOrDefault(zone, 0f) * 100));
            zoneSliders.put(zone, slider);
            row.add(slider, BorderLayout.CENTER);
            zonesPanel.add(row);
        }
        weatherCombo.removeAllItems();
        for (String preset : style.climatePresets().keySet()) {
            weatherCombo.addItem(preset);
        }
        weatherCombo.addItem(CUSTOM);
        weatherCombo.setSelectedItem(
                style.climatePresets().keySet().stream().findFirst().orElse(CUSTOM));
        syncWeatherSliders();
        syncTimeSlider();
        updating = false;
        frame.revalidate();
        frame.repaint();
    }

    private void syncTimeSlider() {
        updating = true;
        timeSlider.setValue(state.minuteOfDay());
        updating = false;
    }

    private void syncWeatherSliders() {
        updating = true;
        weatherSliders.get("rain").setValue(Math.round(state.rain() * 100));
        weatherSliders.get("wind").setValue(Math.round(state.wind() * 100));
        weatherSliders.get("storm").setValue(Math.round(state.storm() * 100));
        updating = false;
    }

    private void updateComposition() {
        Composition composition = engine.compose(style, state.toInput());
        timeLabel.setText(String.format(Locale.ROOT, "%02d:%02d", state.time().getHour(),
                state.time().getMinute()));
        statusLabel.setText(
                String.format(Locale.ROOT, "rain %.2f · wind %.2f · storm %.2f · height %.2f",
                        state.rain(), state.wind(), state.storm(), state.height()));
        renderResults(composition);
    }

    private void renderResults(Composition composition) {
        resultsPanel.removeAll();
        composition.volumes().entrySet().stream().filter(entry -> entry.getValue() > MIN_VOLUME)
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> resultsPanel.add(resultRow(entry.getKey(), entry.getValue())));
        if (resultsPanel.getComponentCount() == 0) {
            resultsPanel.add(new JLabel("(silencio)"));
        }
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private JPanel resultRow(String sound, float volume) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        JLabel name = new JLabel(sound);
        name.setPreferredSize(new Dimension(170, 20));
        row.add(name, BorderLayout.WEST);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(Math.round(Math.min(1f, volume) * 100));
        bar.setStringPainted(false);
        row.add(bar, BorderLayout.CENTER);
        row.add(new JLabel(String.format(Locale.ROOT, "%.2f", volume)), BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return row;
    }
}
