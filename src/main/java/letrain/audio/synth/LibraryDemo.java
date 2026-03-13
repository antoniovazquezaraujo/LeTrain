package letrain.audio.synth;


import javax.swing.*;
import java.awt.*;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryDemo implements TrainSynthesizer.SynthesizerListener {

    private static final Logger log = LoggerFactory.getLogger(LibraryDemo.class);

    private TrainSynthesizer synth;

    // UI Components
    private WaveformPanel locoPanel;
    private WaveformPanel coachPanel;
    private JSlider speedSlider;
    private JLabel speedLabel;
    private JTable notchTable;
    private NotchTableModel tableModel;
    private JFrame frame;

    private static final String RESOURCE_PATH = "/sound/freesound_community-train-17869.wav";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LibraryDemo().createAndShowGUI());
    }

    private void createAndShowGUI() {
        synth = new TrainSynthesizer();
        synth.addListener(this);

        frame = new JFrame("Train Synth Library Demo (Usage Example)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 850);
        frame.setLayout(new BorderLayout());

        // Panels
        locoPanel = new WaveformPanel(synth.getLocoEngine());
        locoPanel.setBorder(BorderFactory.createTitledBorder("Locomotive Layer (Engine)"));

        coachPanel = new WaveformPanel(synth.getCoachEngine());
        coachPanel.setBorder(BorderFactory.createTitledBorder("Coaches Layer (Rattle/Wheels)"));

        // Repaint timer for smooth playhead
        new Timer(16, e -> {
            locoPanel.repaint();
            coachPanel.repaint();
        }).start();

        // Top Control Panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        // 1. File Controls
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadButton = new JButton("Load WAV File");
        JLabel fileLabel = new JLabel("No file loaded");

        // Load default resource
        try {
            java.net.URL url = LibraryDemo.class.getResource(RESOURCE_PATH);
            if (url != null) {
                loadUrl(url, fileLabel);
            } else {
                fileLabel.setText("Default sound not found");
                log.warn("Default sound resource {} not found", RESOURCE_PATH);
            }
        } catch (Exception e) {
            log.error("Error loading default resource {}", RESOURCE_PATH, e);
        }

        loadButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                loadFile(chooser.getSelectedFile(), fileLabel);
            }
        });

        filePanel.add(loadButton);
        filePanel.add(fileLabel);

        // 2. Speed Display (Passive)
        JPanel speedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speedLabel = new JLabel(" Master Speed: 0%");
        speedSlider = new JSlider(0, 100, 0);
        speedSlider.setEnabled(false); // Controlled by Synth

        speedPanel.add(new JLabel("Current Speed:"));
        speedPanel.add(speedLabel);
        speedPanel.add(speedSlider);

        // --- Notch Controls ---
        JPanel notchPanel = new JPanel(new BorderLayout());
        notchPanel.setBorder(BorderFactory.createTitledBorder("Gear/Notch Editor"));

        // Table
        tableModel = new NotchTableModel();
        notchTable = new JTable(tableModel);
        notchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notchTable.getTableHeader().setReorderingAllowed(false);

        // Listeners
        notchTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = notchTable.getSelectedRow();
                SpeedNotch n = synth.getNotch(idx);
                if (n != null) {
                    locoPanel.setLoopRegion(n.loopStart, n.loopEnd);
                    coachPanel.setLoopRegion(n.coachLoopStart, n.coachLoopEnd);
                }
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnCapture = new JButton("Capture Current State to Selected Row");
        btnCapture.addActionListener(e -> captureNotch());
        btnPanel.add(btnCapture);

        // Throttle
        JPanel throttlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel throttleLabel = new JLabel("THROTTLE (0-9): ");
        JSlider throttleSlider = new JSlider(0, 9, 0);
        throttleSlider.setMajorTickSpacing(1);
        throttleSlider.setPaintTicks(true);
        throttleSlider.setPaintLabels(true);
        throttleSlider.setSnapToTicks(true);
        throttleSlider.setPreferredSize(new Dimension(300, 50));

        throttleSlider.addChangeListener(e -> {
            if (!throttleSlider.getValueIsAdjusting()) {
                synth.setThrottle(throttleSlider.getValue());
            }
        });

        throttlePanel.add(throttleLabel);
        throttlePanel.add(throttleSlider);

        JScrollPane tableScroll = new JScrollPane(notchTable);
        tableScroll.setPreferredSize(new Dimension(0, 150));
        notchPanel.add(tableScroll, BorderLayout.CENTER);
        notchPanel.add(btnPanel, BorderLayout.NORTH);
        notchPanel.add(throttlePanel, BorderLayout.SOUTH);

        controlPanel.add(notchPanel);

        // 3. Independent Volume Controls
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        volumePanel.setBorder(BorderFactory.createTitledBorder("Mixer"));

        JSlider volLoco = new JSlider(0, 100, 80);
        JSlider volCoach = new JSlider(0, 100, 60);

        volLoco.addChangeListener(e -> synth.setLocoVolume(volLoco.getValue() / 100.0f));
        volCoach.addChangeListener(e -> synth.setCoachVolume(volCoach.getValue() / 100.0f));

        volumePanel.add(new JLabel("Loco Vol:"));
        volumePanel.add(volLoco);
        volumePanel.add(new JLabel("Coach Vol:"));
        volumePanel.add(volCoach);

        controlPanel.add(filePanel);
        controlPanel.add(speedPanel);
        controlPanel.add(volumePanel);

        frame.add(controlPanel, BorderLayout.NORTH);

        JPanel waveContainer = new JPanel(new GridLayout(2, 1));
        waveContainer.add(locoPanel);
        waveContainer.add(coachPanel);
        frame.add(waveContainer, BorderLayout.CENTER);

        synth.startAudio();
        synth.setLocoVolume(0.8f);
        synth.setCoachVolume(0.6f);

        frame.setVisible(true);
    }

    // --- Synthesizer Listener ---
    @Override
    public void onSpeedUpdate(float speed) {
        speedSlider.setValue((int) (speed * 100));
        speedLabel.setText(String.format(" Master Speed: %d%%", (int) (speed * 100)));
    }

    @Override
    public void onNotchChanged(int notchIndex) {
        if (notchTable != null) {
            notchTable.setRowSelectionInterval(notchIndex, notchIndex);
        }
    }

    // --- Helpers ---
    private void loadUrl(java.net.URL url, JLabel label) {
        try {
            AudioSample sample = new AudioSample(url);
            synth.setSample(sample);
            locoPanel.setAudioSample(sample);
            coachPanel.setAudioSample(sample);
            label.setText(new File(url.getFile()).getName());
            if (tableModel != null)
                tableModel.fireTableDataChanged();
        } catch (Exception e) {
            log.error("Error loading audio sample from url {}", url, e);
        }
    }

    private void loadFile(File f, JLabel label) {
        try {
            AudioSample sample = new AudioSample(f);
            synth.setSample(sample);
            locoPanel.setAudioSample(sample);
            coachPanel.setAudioSample(sample);
            label.setText(f.getName());
            if (tableModel != null)
                tableModel.fireTableDataChanged();
        } catch (Exception e) {
            log.error("Error loading audio sample from file {}", f, e);
        }
    }

    private void captureNotch() {
        int idx = notchTable.getSelectedRow();
        if (idx < 0)
            return;

        SpeedNotch n = synth.getNotch(idx);
        if (n == null)
            return;

        // Capture logic: We grab current state from engines
        // But Synth manages engines. We can access engines via synth helpers or
        // getters.
        GrainEngine loco = synth.getLocoEngine();
        GrainEngine coach = synth.getCoachEngine();

        n.cruiseSpeed = loco.getSpeed();
        // Intelligent defaults
        if (n.startSpeed == 0)
            n.startSpeed = Math.max(0, n.cruiseSpeed - 0.1f);
        if (n.endSpeed == 0)
            n.endSpeed = Math.min(1, n.cruiseSpeed + 0.1f);

        n.loopStart = (float) loco.getLoopStart();
        n.loopEnd = (float) loco.getLoopEnd();
        n.coachLoopStart = (float) coach.getLoopStart();
        n.coachLoopEnd = (float) coach.getLoopEnd();

        tableModel.fireTableRowsUpdated(idx, idx);
    }

    // --- Table Model ---
    // Copied and adapted to use Synth
    class NotchTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columns = { "Notch", "Start (%)", "Cruise (%)", "End (%)", "Loco Start", "Loco End",
                "Coach Start", "Coach End", "Ramp (s)" };

        public int getRowCount() {
            return 10;
        }

        public int getColumnCount() {
            return columns.length;
        }

        public String getColumnName(int col) {
            return columns[col];
        }

        public boolean isCellEditable(int row, int col) {
            return col > 0;
        }

        public Object getValueAt(int row, int col) {
            SpeedNotch n = synth.getNotch(row);
            if (n == null)
                return null;

            float totalMs = 1;
            GrainEngine loco = synth.getLocoEngine();
            if (loco != null && loco.getSample() != null) {
                totalMs = (loco.getSample().getLength() / loco.getSample().getSampleRate()) * 1000;
            }

            switch (col) {
                case 0:
                    return row;
                case 1:
                    return (int) (n.startSpeed * 100);
                case 2:
                    return (int) (n.cruiseSpeed * 100);
                case 3:
                    return (int) (n.endSpeed * 100);
                case 4:
                    return (int) (n.loopStart * totalMs);
                case 5:
                    return (int) (n.loopEnd * totalMs);
                case 6:
                    return (int) (n.coachLoopStart * totalMs);
                case 7:
                    return (int) (n.coachLoopEnd * totalMs);
                case 8:
                    return n.rampTime;
                default:
                    return null;
            }
        }

        public void setValueAt(Object val, int row, int col) {
            try {
                SpeedNotch n = synth.getNotch(row);
                if (n == null)
                    return;

                float totalMs = 1000;
                GrainEngine loco = synth.getLocoEngine();
                if (loco != null && loco.getSample() != null) {
                    totalMs = (loco.getSample().getLength() / loco.getSample().getSampleRate()) * 1000;
                }

                String sVal = val.toString();
                switch (col) {
                    case 1:
                        n.startSpeed = Float.parseFloat(sVal) / 100.0f;
                        break;
                    case 2:
                        n.cruiseSpeed = Float.parseFloat(sVal) / 100.0f;
                        break;
                    case 3:
                        n.endSpeed = Float.parseFloat(sVal) / 100.0f;
                        break;
                    case 4:
                        n.loopStart = Float.parseFloat(sVal) / totalMs;
                        break;
                    case 5:
                        n.loopEnd = Float.parseFloat(sVal) / totalMs;
                        break;
                    case 6:
                        n.coachLoopStart = Float.parseFloat(sVal) / totalMs;
                        break;
                    case 7:
                        n.coachLoopEnd = Float.parseFloat(sVal) / totalMs;
                        break;
                    case 8:
                        n.rampTime = Float.parseFloat(sVal);
                        break;
                }
                fireTableCellUpdated(row, col);
            } catch (Exception e) {
            }
        }

        public Class<?> getColumnClass(int col) {
            if (col == 8)
                return Float.class;
            return Integer.class;
        }
    }

    // WaveformPanel needs to be copied here or extracted too?
    // It's static inner in TrainDemo. I should copy it to here as inner class or
    // make it standalone.
    // For simplicity, I'll copy it as inner class here since it's UI.
    static class WaveformPanel extends JPanel {
        private AudioSample sample;
        private GrainEngine engine;
        private float loopStart = 0, loopEnd = 1;
        private float dragAnchor = 0;

        public WaveformPanel(GrainEngine engine) {
            this.engine = engine;
            this.setBackground(Color.BLACK);
            this.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            java.awt.event.MouseAdapter input = new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (sample == null)
                        return;
                    dragAnchor = Math.max(0f, Math.min(1f, e.getX() / (float) getWidth()));
                    loopStart = dragAnchor;
                    loopEnd = dragAnchor;
                    updateEngine();
                    repaint();
                }

                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (sample == null)
                        return;
                    float cur = Math.max(0f, Math.min(1f, e.getX() / (float) getWidth()));
                    loopStart = Math.min(cur, dragAnchor);
                    loopEnd = Math.max(cur, dragAnchor);
                    updateEngine();
                    repaint();
                }
            };
            this.addMouseListener(input);
            this.addMouseMotionListener(input);
        }

        public void setAudioSample(AudioSample s) {
            this.sample = s;
            this.loopStart = 0f;
            this.loopEnd = 1f;
            updateEngine();
            repaint();
        }

        public void setLoopRegion(float start, float end) {
            this.loopStart = start;
            this.loopEnd = end;
            repaint();
        }

        private void updateEngine() {
            if (engine != null)
                engine.setLoopPoints(loopStart, loopEnd);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (sample == null) {
                g.setColor(Color.WHITE);
                g.drawString("Load a WAV...", 20, 30);
                return;
            }
            g.setColor(new Color(20, 20, 20));
            g.fillRect(0, 0, getWidth(), getHeight());

            // Draw Waveform
            g.setColor(new Color(0, 255, 100));
            int w = getWidth(), h = getHeight(), h2 = h / 2;
            int step = Math.max(1, sample.getLength() / w);
            for (int x = 0; x < w; x++) {
                int idx = x * step;
                if (idx >= sample.getLength())
                    break;
                // Simple point drawing for speed
                float val = sample.getSample(idx);
                int y = (int) (h2 - (val * h2));
                g.drawLine(x, y, x, y);
            }

            // Draw Loop
            int x1 = (int) (loopStart * w), x2 = (int) (loopEnd * w);
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, x1, h);
            g.fillRect(x2, 0, w - x2, h);
            g.setColor(Color.RED);
            g.drawLine(x1, 0, x1, h);
            g.drawLine(x2, 0, x2, h);

            // Draw Playhead
            if (engine != null) {
                int xp = (int) (engine.getPositionNormalized() * w);
                g.setColor(Color.CYAN);
                g.drawLine(xp, 0, xp, h);
            }
        }
    }
}
