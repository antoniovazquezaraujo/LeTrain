package letrain.soundscape.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import letrain.soundscape.Composition;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.SpeedPreset;
import letrain.soundscape.audio.AmbientPlayer;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;
import letrain.soundscape.impl.TextStyleWriter;

/**
 * Simple Swing playground for a style file: move time, zone weights, height and weather and watch
 * the target mix change live. No audio yet; it prints the same numbers the engine feeds the mixer.
 */
public final class SoundscapePlayer {

    private static final String DEFAULT_STYLE = "/styles/valle-norte.sound";
    private static final String CUSTOM = "(custom)";

    private final TextStyleLoader loader = new TextStyleLoader();
    private final TextStyleWriter writer = new TextStyleWriter();
    private final SoundscapeEngine engine = new SoundscapeEngineImpl();

    private SoundscapeStyle style;
    private PlayerState state;
    private String styleName = "valle-norte.sound";
    private List<String> sourceLines = List.of();

    private JFrame frame;
    private JLabel timeLabel;
    private JLabel statusLabel;
    private JLabel styleLabel;
    private JSlider timeSlider;
    private JSlider heightSlider;
    private final Map<String, JSlider> weatherSliders = new LinkedHashMap<>();
    private JComboBox<String> weatherCombo;
    private JComboBox<SpeedPreset> speedCombo;
    private JComboBox<String> previewCombo;
    private int previewMultiplier = 1;
    private double minutesAccumulator;
    private JPanel zonesPanel;
    private JPanel resultsPanel;
    private final Map<String, JSlider> zoneSliders = new LinkedHashMap<>();
    private final Map<String, JSlider> gainSliders = new LinkedHashMap<>();
    private final Map<String, JProgressBar> volumeBars = new LinkedHashMap<>();
    private final Map<String, JLabel> volumeLabels = new LinkedHashMap<>();

    private boolean updating;
    private Timer playTimer;
    private JToggleButton listenButton;
    private AmbientPlayer ambientPlayer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SoundscapePlayer().show(args));
    }

    void show(String[] args) {
        try {
            if (args.length > 0) {
                Path path = Path.of(args[0]);
                sourceLines = loader.readLines(path);
                style = loader.parse(sourceLines);
                styleName = path.getFileName().toString();
            } else {
                sourceLines = loader.readResourceLines(DEFAULT_STYLE);
                style = loader.parse(sourceLines);
                styleName = "valle-norte.sound";
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not load style: " + e.getMessage(),
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
        frame.add(buildControlsScroll(), BorderLayout.WEST);
        frame.add(new JScrollPane(buildResultsPanel()), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setSize(900, 640);
        frame.setLocationRelativeTo(null);
    }

    /** Right panel: every sound with its gain slider and a live VU meter. */
    JPanel buildResultsPanel() {
        resultsPanel = new JPanel(new GridBagLayout());
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return resultsPanel;
    }

    /** Loads a style and prepares the state without opening a window (layout tests). */
    void initStyle(SoundscapeStyle style, String name) {
        initStyle(style, name, List.of());
    }

    /**
     * Loads a style, its source text and the state without opening a window (calibration tests).
     */
    void initStyle(SoundscapeStyle style, String name, List<String> sourceLines) {
        this.style = style;
        this.styleName = name;
        this.sourceLines = List.copyOf(sourceLines);
        this.state = new PlayerState(style);
    }

    /** Builds the controls column; package-private so tests can lay it out without a window. */
    JScrollPane buildControlsScroll() {
        ScrollablePanel controls = new ScrollablePanel();
        controls.setLayout(new GridBagLayout());
        controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        styleLabel = new JLabel();
        styleLabel.setHorizontalAlignment(SwingConstants.LEADING);
        header.add(styleLabel, BorderLayout.CENTER);
        JPanel styleButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton loadButton = new JButton("Load style…");
        loadButton.addActionListener(e -> chooseStyle());
        styleButtons.add(loadButton);
        JButton exportButton = new JButton("Export style…");
        exportButton.addActionListener(e -> exportStyle());
        styleButtons.add(exportButton);
        header.add(styleButtons, BorderLayout.EAST);
        addRow(controls, header, true);

        addRow(controls, section("Time"), true);
        timeLabel = new JLabel();
        addRow(controls, timeLabel, true);
        timeSlider = new JSlider(0, 24 * 60 - 1, state.minuteOfDay());
        timeSlider.addChangeListener(e -> {
            if (!updating) {
                state.setMinuteOfDay(timeSlider.getValue());
                updateComposition();
            }
        });
        addRow(controls, timeSlider, true);
        JButton playButton = new JButton("▶ Full day");
        playButton.addActionListener(e -> togglePlay(playButton));
        addRow(controls, playButton, false);
        listenButton = new JToggleButton("🔊 Listen");
        listenButton.addActionListener(e -> toggleAudio());
        addRow(controls, listenButton, false);

        addRow(controls, section("Height (zoom)"), true);
        heightSlider = slider(100, 0);
        addRow(controls, heightSlider, true);

        addRow(controls, section("Speed"), true);
        speedCombo = new JComboBox<>(SpeedPreset.values());
        speedCombo.setSelectedItem(state.speed());
        speedCombo.addActionListener(e -> {
            if (!updating) {
                state.setSpeed((SpeedPreset) speedCombo.getSelectedItem());
                updateComposition();
            }
        });
        addRow(controls, speedCombo, true);
        previewCombo = new JComboBox<>(new String[] {"x1", "x10", "x60"});
        previewCombo.addActionListener(e -> {
            String selected = (String) previewCombo.getSelectedItem();
            previewMultiplier = selected == null ? 1 : Integer.parseInt(selected.substring(1));
            updateComposition();
        });
        addRow(controls, labelRow("preview", 80, previewCombo), true);

        addRow(controls, section("Weather"), true);
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
        addRow(controls, weatherCombo, true);
        addRow(controls, weatherSliderRow("rain"), true);
        addRow(controls, weatherSliderRow("wind"), true);
        addRow(controls, weatherSliderRow("storm"), true);

        addRow(controls, section("Zones"), true);
        zonesPanel = new JPanel(new GridBagLayout());
        addRow(controls, zonesPanel, true);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = controls.getComponentCount();
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        controls.add(Box.createGlue(), filler);

        JScrollPane scroll = new JScrollPane(controls);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(360, 600));
        return scroll;
    }

    /** Adds a row to a GridBagLayout panel; stretch=false keeps natural size at the west. */
    private void addRow(JPanel panel, Component component, boolean stretch) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = panel.getComponentCount();
        constraints.weightx = 1;
        constraints.fill = stretch ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(2, 0, 2, 0);
        panel.add(component, constraints);
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
        return labelRow(key, 80, slider);
    }

    /** One control row with a fixed label width, so every slider starts at the same x. */
    private JPanel labelRow(String text, int labelWidth, Component component) {
        JLabel label = new JLabel(text);
        Dimension size = new Dimension(labelWidth, 18);
        label.setPreferredSize(size);
        label.setMinimumSize(size);
        label.setMaximumSize(size);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return row;
    }

    /** Visible for layout tests: the zone sliders by zone name. */
    Map<String, JSlider> zoneSliders() {
        return zoneSliders;
    }

    /** Visible for calibration tests: the gain sliders by sound key. */
    Map<String, JSlider> gainSliders() {
        return gainSliders;
    }

    /** Visible for calibration tests: the VU meters by sound key. */
    Map<String, JProgressBar> volumeBars() {
        return volumeBars;
    }

    /** Visible for calibration tests: the right calibration panel. */
    JPanel resultsPanel() {
        return resultsPanel;
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
            button.setText("▶ Full day");
            return;
        }
        button.setText("⏸ Pause");
        minutesAccumulator = 0;
        playTimer = new Timer(50, e -> {
            double perSecond = state.speed().gameMinutesPerRealSecond() * previewMultiplier;
            minutesAccumulator += perSecond * 0.05;
            int advance = (int) minutesAccumulator;
            if (advance <= 0) {
                return;
            }
            minutesAccumulator -= advance;
            state.advance(advance);
            syncTimeSlider();
            updateComposition();
        });
        playTimer.start();
    }

    private void chooseStyle() {
        stopAudio();
        if (listenButton != null) {
            listenButton.setSelected(false);
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Soundscape style (*.sound)", "sound"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            sourceLines = loader.readLines(file.toPath());
            style = loader.parse(sourceLines);
            styleName = file.getName();
            state = new PlayerState(style);
            rebuildForStyle();
            updateComposition();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not load: " + e.getMessage(), "soundscape",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void rebuildForStyle() {
        updating = true;
        styleLabel.setText(styleName);
        styleLabel.setToolTipText(styleName);
        zoneSliders.clear();
        zonesPanel.removeAll();
        int zoneLabelWidth = 0;
        for (String zone : style.zones().keySet()) {
            zoneLabelWidth = Math.max(zoneLabelWidth, new JLabel(zone).getPreferredSize().width);
        }
        zoneLabelWidth += 6;
        for (String zone : style.zones().keySet()) {
            JSlider slider =
                    slider(100, Math.round(state.zoneWeights().getOrDefault(zone, 0f) * 100));
            zoneSliders.put(zone, slider);
            addRow(zonesPanel, labelRow(zone, zoneLabelWidth, slider), true);
        }
        if (resultsPanel != null) {
            gainSliders.clear();
            volumeBars.clear();
            volumeLabels.clear();
            resultsPanel.removeAll();
            List<String> gainKeys = gainKeys();
            int nameWidth = 0;
            for (String key : gainKeys) {
                nameWidth = Math.max(nameWidth, new JLabel(key).getPreferredSize().width);
            }
            nameWidth += 6;
            addResultsHeader(nameWidth);
            int row = 1;
            for (String key : gainKeys) {
                addResultsRow(key, nameWidth, row++);
            }
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
        if (frame != null) {
            frame.revalidate();
            frame.repaint();
        }
    }

    /** Every key the engine can multiply: catalog sounds plus the provided weather and height. */
    private List<String> gainKeys() {
        List<String> keys = new ArrayList<>(style.sounds().keySet());
        for (String weather : style.weatherSounds().keySet()) {
            keys.add("weather-" + weather);
        }
        for (String height : style.heightSounds().keySet()) {
            keys.add("height-" + height);
        }
        return keys;
    }

    private void addResultsHeader(int nameWidth) {
        JLabel sound = new JLabel("sound");
        sound.setPreferredSize(new Dimension(nameWidth, 18));
        addCell(resultsPanel, sound, 0, 0, 0, GridBagConstraints.NONE);
        addCell(resultsPanel, new JLabel("gain"), 1, 0, 0, GridBagConstraints.NONE);
        addCell(resultsPanel, new JLabel("level"), 3, 0, 1, GridBagConstraints.HORIZONTAL);
    }

    /** One calibration row: sound, 0-200% gain slider, its percentage and a live VU meter. */
    private void addResultsRow(String key, int nameWidth, int row) {
        JLabel name = new JLabel(key);
        name.setPreferredSize(new Dimension(nameWidth, 18));
        addCell(resultsPanel, name, 0, row, 0, GridBagConstraints.NONE);

        JSlider slider = new JSlider(0, 200, Math.round(style.gainOf(key) * 100));
        slider.setPreferredSize(new Dimension(130, 16));
        JLabel percent = new JLabel(slider.getValue() + "%");
        percent.setPreferredSize(new Dimension(42, 18));
        percent.setHorizontalAlignment(SwingConstants.RIGHT);
        slider.addChangeListener(e -> {
            percent.setText(slider.getValue() + "%");
            applyGain(key, slider.getValue() / 100f);
        });
        gainSliders.put(key, slider);
        addCell(resultsPanel, slider, 1, row, 0, GridBagConstraints.HORIZONTAL);
        addCell(resultsPanel, percent, 2, row, 0, GridBagConstraints.NONE);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setPreferredSize(new Dimension(120, 16));
        volumeBars.put(key, bar);
        addCell(resultsPanel, bar, 3, row, 1, GridBagConstraints.HORIZONTAL);

        JLabel volume = new JLabel("0.00");
        volume.setPreferredSize(new Dimension(42, 18));
        volume.setHorizontalAlignment(SwingConstants.RIGHT);
        volumeLabels.put(key, volume);
        addCell(resultsPanel, volume, 4, row, 0, GridBagConstraints.NONE);
    }

    private void addCell(JPanel panel, Component component, int x, int y, double weightx,
            int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weightx;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(2, 3, 2, 3);
        panel.add(component, constraints);
    }

    private void applyGain(String key, float gain) {
        Map<String, Float> updated = new LinkedHashMap<>(style.gains());
        if (Math.abs(gain - 1f) < 1e-3f) {
            updated.remove(key);
        } else {
            updated.put(key, gain);
        }
        style = style.withGains(updated);
        updateComposition();
    }

    /** Exports the calibration as text, keeping the original file and replacing only [gains]. */
    List<String> exportLines() {
        return writer.withGains(sourceLines, style.gains());
    }

    private void exportStyle() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Soundscape style (*.sound)", "sound"));
        chooser.setSelectedFile(new File(defaultExportName()));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        try {
            writer.write(target, sourceLines, style.gains());
            statusLabel.setText("exported " + target.getFileName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not export: " + e.getMessage(),
                    "soundscape", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String defaultExportName() {
        String base = styleName.endsWith(".sound")
                ? styleName.substring(0, styleName.length() - ".sound".length())
                : styleName;
        return base + "-calibrated.sound";
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

    private void toggleAudio() {
        if (!listenButton.isSelected()) {
            stopAudio();
            return;
        }
        stopAudio();
        AmbientPlayer player = new AmbientPlayer(style, 1);
        if (player.sampleCount() == 0) {
            JOptionPane.showMessageDialog(frame,
                    "No sound could be loaded. Check the style materials.", "soundscape",
                    JOptionPane.WARNING_MESSAGE);
            listenButton.setSelected(false);
            return;
        }
        try {
            player.start();
            ambientPlayer = player;
            if (!player.missingSounds().isEmpty()) {
                statusLabel.setText("missing sounds: " + player.missingSounds());
            }
        } catch (javax.sound.sampled.LineUnavailableException e) {
            JOptionPane.showMessageDialog(frame, "No audio device: " + e.getMessage(), "soundscape",
                    JOptionPane.ERROR_MESSAGE);
            listenButton.setSelected(false);
        }
        updateComposition();
    }

    private void stopAudio() {
        if (ambientPlayer != null) {
            ambientPlayer.close();
            ambientPlayer = null;
        }
    }

    private void updateComposition() {
        Composition composition = engine.compose(style, state.toInput());
        if (ambientPlayer != null) {
            ambientPlayer.updateTargets(composition);
        }
        timeLabel.setText(String.format(Locale.ROOT, "%02d:%02d", state.time().getHour(),
                state.time().getMinute()));
        if (statusLabel != null) {
            statusLabel.setText(String.format(Locale.ROOT,
                    "rain %.2f · wind %.2f · storm %.2f · height %.2f · %s · preview x%d",
                    state.rain(), state.wind(), state.storm(), state.height(), state.speed(),
                    previewMultiplier));
        }
        if (resultsPanel != null) {
            updateMeters(composition);
        }
    }

    /** Moves every VU meter to the sound's current composed volume. */
    private void updateMeters(Composition composition) {
        for (Map.Entry<String, JProgressBar> meter : volumeBars.entrySet()) {
            float volume = composition.volumeOf(meter.getKey());
            meter.getValue().setValue(Math.round(Math.min(1f, volume) * 100));
            JLabel label = volumeLabels.get(meter.getKey());
            if (label != null) {
                label.setText(String.format(Locale.ROOT, "%.2f", volume));
            }
        }
    }

    /** Scroll view that always matches the viewport width, so rows never overflow horizontally. */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation,
                int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation,
                int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

}
