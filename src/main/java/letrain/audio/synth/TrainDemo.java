package letrain.audio.synth;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;

public class TrainDemo {
    private static boolean running = true;

    // Two engines for multi-layer synthesis
    private static GrainEngine locoEngine;
    private static GrainEngine coachEngine;

    // Speed Notch System
    private static class SpeedNotch {
        String name;
        float startSpeed; // Entry speed from lower gear
        float cruiseSpeed; // Steady state speed
        float endSpeed; // Exit speed to higher gear
        float loopStart, loopEnd; // Loco
        float coachLoopStart, coachLoopEnd; // Coach
        float rampTime; // Seconds per phase

        public SpeedNotch(String name, float startSpeed, float cruiseSpeed, float endSpeed, float start, float end,
                float coachStart,
                float coachEnd,
                float rampTime) {
            this.name = name;
            this.startSpeed = startSpeed;
            this.cruiseSpeed = cruiseSpeed;
            this.endSpeed = endSpeed;
            this.loopStart = start;
            this.loopEnd = end;
            this.coachLoopStart = coachStart;
            this.coachLoopEnd = coachEnd;
            this.rampTime = rampTime;
        }
    }

    private static SpeedNotch[] notches = new SpeedNotch[10];
    private static int currentNotchIndex = 0;
    private static boolean isTransitioning = false;
    private static Timer transitionTimer;

    // UI for Notch Editor
    private static JTable notchTable;
    private static NotchTableModel tableModel;

    private static void initNotches() {
        for (int i = 0; i < 10; i++) {
            float base = i / 10.0f;
            float start = base;
            float cruise = base + 0.05f;
            float end = base + 0.10f;

            if (i == 0) {
                start = 0;
                cruise = 0;
                end = 0.1f;
            } // Idle

            // Default placeholders
            notches[i] = new SpeedNotch("Notch " + i, start, cruise, end, 0.0f, 0.2f, 0.0f, 0.2f, 2.0f);
        }
        notches[0].name = "Idle";
    }

    // Table Model
    static class NotchTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columns = { "Notch", "Start (%)", "Cruise (%)", "End (%)", "Loco Start", "Loco End",
                "Coach Start",
                "Coach End", "Ramp (s)" };

        @Override
        public int getRowCount() {
            return notches.length;
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col > 0; // All editable except Notch index
        }

        @Override
        public Object getValueAt(int row, int col) {
            SpeedNotch n = notches[row];
            float totalMs = 0;
            if (locoEngine != null && locoEngine.getSample() != null) {
                totalMs = (locoEngine.getSample().getLength() / locoEngine.getSample().getSampleRate()) * 1000;
            }
            if (totalMs == 0)
                totalMs = 1;

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

        @Override
        public void setValueAt(Object val, int row, int col) {
            try {
                SpeedNotch n = notches[row];
                float totalMs = 0;
                if (locoEngine != null && locoEngine.getSample() != null) {
                    totalMs = (locoEngine.getSample().getLength() / locoEngine.getSample().getSampleRate()) * 1000;
                }
                if (totalMs == 0)
                    totalMs = 1000;

                String sVal = val.toString();

                switch (col) {
                    case 1: // Start Speed %
                        float s1 = Float.parseFloat(sVal);
                        n.startSpeed = Math.max(0, Math.min(100, s1)) / 100.0f;
                        break;
                    case 2: // Cruise Speed %
                        float sc = Float.parseFloat(sVal);
                        n.cruiseSpeed = Math.max(0, Math.min(100, sc)) / 100.0f;
                        break;
                    case 3: // End Speed %
                        float s2 = Float.parseFloat(sVal);
                        n.endSpeed = Math.max(0, Math.min(100, s2)) / 100.0f;
                        break;
                    case 4: // Loco Start
                        float start = Float.parseFloat(sVal);
                        n.loopStart = Math.max(0, Math.min(1, start / totalMs));
                        break;
                    case 5: // Loco End
                        float end = Float.parseFloat(sVal);
                        n.loopEnd = Math.max(0, Math.min(1, end / totalMs));
                        break;
                    case 6: // Coach Start
                        float cStart = Float.parseFloat(sVal);
                        n.coachLoopStart = Math.max(0, Math.min(1, cStart / totalMs));
                        break;
                    case 7: // Coach End
                        float cEnd = Float.parseFloat(sVal);
                        n.coachLoopEnd = Math.max(0, Math.min(1, cEnd / totalMs));
                        break;
                    case 8: // Ramp s
                        float r = Float.parseFloat(sVal);
                        n.rampTime = Math.max(0.1f, r);
                        break;
                }
                fireTableCellUpdated(row, col);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 8)
                return Float.class;
            return Integer.class;
        }
    }

    // UI Components
    private static WaveformPanel locoPanel;
    private static WaveformPanel coachPanel;

    // User provided path
    private static final String RESOURCE_PATH = "/sound/freesound_community-train-17869.wav";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Multi-Layer Train Engine (Loco + Coaches)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800);
        frame.setLayout(new BorderLayout());

        // Initialize Engines
        locoEngine = new GrainEngine();
        coachEngine = new GrainEngine();

        // Defaults
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG); // Loco also Ping-Pongs now
        coachEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);

        // Panels
        locoPanel = new WaveformPanel(locoEngine);
        locoPanel.setBorder(BorderFactory.createTitledBorder("Locomotive Layer (Engine) - PingPong"));

        coachPanel = new WaveformPanel(coachEngine);
        coachPanel.setBorder(BorderFactory.createTitledBorder("Coaches Layer (Rattle/Wheels) - PingPong"));

        // Repaint timer for smooth playhead movement
        new Timer(16, e -> {
            locoPanel.repaint();
            coachPanel.repaint();
        }).start();

        // Initialize Notches
        initNotches();

        // Top Control Panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        // 1. File Controls
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadButton = new JButton("Load WAV File");
        JLabel fileLabel = new JLabel("No file loaded");

        // Load default resource
        try {
            java.net.URL url = TrainDemo.class.getResource(RESOURCE_PATH);
            if (url != null) {
                loadUrl(url, fileLabel, frame);
            } else {
                fileLabel.setText("Default sound not found in resources");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        loadButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                loadFile(chooser.getSelectedFile(), fileLabel, frame);
            }
        });

        filePanel.add(loadButton);
        filePanel.add(fileLabel);

        // 2. Speed Control (Master)
        JPanel speedPanel = new JPanel(new BorderLayout());
        JPanel speedControls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel speedLabel = new JLabel(" Master Speed: 0%");
        JSlider speedSlider = new JSlider(0, 100, 0);

        speedControls.add(speedLabel);

        speedPanel.add(speedControls, BorderLayout.WEST);
        speedPanel.add(speedSlider, BorderLayout.CENTER);

        speedSlider.addChangeListener(e -> {
            float val = speedSlider.getValue() / 100.0f;
            locoEngine.setSpeed(val);
            coachEngine.setSpeed(val);
            speedLabel.setText(String.format(" Master Speed: %d%%", speedSlider.getValue()));
        });

        // --- Notch Controls ---
        JPanel notchPanel = new JPanel(new BorderLayout());
        notchPanel.setBorder(BorderFactory.createTitledBorder("Gear/Notch Editor (Table)"));

        // Table
        tableModel = new NotchTableModel();
        notchTable = new JTable(tableModel);
        notchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notchTable.getTableHeader().setReorderingAllowed(false);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnCapture = new JButton("Capture Current State to Selected Row");

        btnPanel.add(btnCapture);

        // Bottom: Throttle
        JPanel throttlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel throttleLabel = new JLabel("THROTTLE (0-9): ");
        JSlider throttleSlider = new JSlider(0, 9, 0); // 0-9 Range
        throttleSlider.setMajorTickSpacing(1);
        throttleSlider.setPaintTicks(true);
        throttleSlider.setPaintLabels(true);
        throttleSlider.setSnapToTicks(true);
        throttleSlider.setPreferredSize(new Dimension(300, 50));

        throttlePanel.add(throttleLabel);
        throttlePanel.add(throttleSlider);

        JScrollPane tableScroll = new JScrollPane(notchTable);
        tableScroll.setPreferredSize(new Dimension(0, 150)); // Limit height to ~8-9 rows
        notchPanel.add(tableScroll, BorderLayout.CENTER);
        notchPanel.add(btnPanel, BorderLayout.NORTH);
        notchPanel.add(throttlePanel, BorderLayout.SOUTH);

        // Listeners
        notchTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = notchTable.getSelectedRow();
                if (idx >= 0 && idx < notches.length) {
                    SpeedNotch n = notches[idx];
                    if (locoPanel != null)
                        locoPanel.setLoopRegion(n.loopStart, n.loopEnd);
                    if (coachPanel != null)
                        coachPanel.setLoopRegion(n.coachLoopStart, n.coachLoopEnd);
                }
            }
        });

        btnCapture.addActionListener(e -> {
            int idx = notchTable.getSelectedRow();
            if (idx >= 0) {
                captureNotch(idx);
                tableModel.fireTableRowsUpdated(idx, idx);
            } else {
                JOptionPane.showMessageDialog(frame, "Select a row in the table first!");
            }
        });

        throttleSlider.addChangeListener(e -> {
            if (!throttleSlider.getValueIsAdjusting()) {
                transitionToNotch(throttleSlider.getValue(), speedSlider, speedLabel);
            }
        });

        controlPanel.add(notchPanel);

        // 3. Layer Controls
        JPanel layerPanel = new JPanel(new GridLayout(1, 2));

        // Loco Controls
        JPanel locoCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox locoMute = new JCheckBox("Mute Loco");
        JSlider locoVol = new JSlider(0, 100, 80);

        JLabel locoFlipLabel = new JLabel("Flip: 0%");
        JSlider locoFlip = new JSlider(0, 100, 0); // 0..1 probability
        locoFlip.setPreferredSize(new Dimension(80, 20));

        JLabel locoDurLabel = new JLabel("Dur: 1.0s");
        JSlider locoDur = new JSlider(1, 50, 10); // 0.1s to 5.0s
        locoDur.setPreferredSize(new Dimension(80, 20));

        locoCtrl.add(new JLabel("Loco Vol:"));
        locoCtrl.add(locoVol);
        locoCtrl.add(locoMute);
        locoCtrl.add(locoFlipLabel);
        locoCtrl.add(locoFlip);
        locoCtrl.add(locoDurLabel);
        locoCtrl.add(locoDur);

        locoVol.addChangeListener(e -> locoEngine.setVolume(locoMute.isSelected() ? 0 : locoVol.getValue() / 100.0f));
        locoMute.addActionListener(e -> locoEngine.setVolume(locoMute.isSelected() ? 0 : locoVol.getValue() / 100.0f));

        locoFlip.addChangeListener(e -> {
            float val = locoFlip.getValue() / 100.0f;
            locoEngine.setTurnProbability(val);
            locoFlipLabel.setText(String.format("Flip: %d%%", locoFlip.getValue()));
        });

        locoDur.addChangeListener(e -> {
            float sec = locoDur.getValue() / 10.0f;
            locoEngine.setReverseDuration(sec);
            locoDurLabel.setText(String.format("Dur: %.1fs", sec));
        });

        locoEngine.setVolume(0.8f); // Set initial volume

        // Coach Controls
        JPanel coachCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox coachMute = new JCheckBox("Mute Coach");
        JSlider coachVol = new JSlider(0, 100, 60);

        JLabel coachFlipLabel = new JLabel("Flip: 0%");
        JSlider coachFlip = new JSlider(0, 100, 0);
        coachFlip.setPreferredSize(new Dimension(80, 20));

        JLabel coachDurLabel = new JLabel("Dur: 1.0s");
        JSlider coachDur = new JSlider(1, 50, 10);
        coachDur.setPreferredSize(new Dimension(80, 20));

        coachCtrl.add(new JLabel("Coach Vol:"));
        coachCtrl.add(coachVol);
        coachCtrl.add(coachMute);
        coachCtrl.add(coachFlipLabel);
        coachCtrl.add(coachFlip);
        coachCtrl.add(coachDurLabel);
        coachCtrl.add(coachDur);

        coachVol.addChangeListener(
                e -> coachEngine.setVolume(coachMute.isSelected() ? 0 : coachVol.getValue() / 100.0f));
        coachMute.addActionListener(
                e -> coachEngine.setVolume(coachMute.isSelected() ? 0 : coachVol.getValue() / 100.0f));

        coachFlip.addChangeListener(e -> {
            float val = coachFlip.getValue() / 100.0f;
            coachEngine.setTurnProbability(val);
            coachFlipLabel.setText(String.format("Flip: %d%%", coachFlip.getValue()));
        });

        coachDur.addChangeListener(e -> {
            float sec = coachDur.getValue() / 10.0f;
            coachEngine.setReverseDuration(sec);
            coachDurLabel.setText(String.format("Dur: %.1fs", sec));
        });

        coachEngine.setVolume(0.6f); // Set initial volume

        layerPanel.add(locoCtrl);
        layerPanel.add(coachCtrl);

        controlPanel.add(filePanel);
        controlPanel.add(speedPanel);
        controlPanel.add(layerPanel);
        controlPanel.add(new JLabel(
                "  Loco (Top) = Engine Chug.   Coaches (Bottom) = Wheel Clatter.   Select different regions!"));

        // Layout
        frame.add(controlPanel, BorderLayout.NORTH);

        JPanel waveContainer = new JPanel(new GridLayout(2, 1));
        waveContainer.add(locoPanel);
        waveContainer.add(coachPanel);
        frame.add(waveContainer, BorderLayout.CENTER);

        // Start Audio Thread
        new Thread(() -> audioLoop(locoEngine, coachEngine)).start();

        frame.setVisible(true);
    }

    private static void loadFile(File f, JLabel label, JFrame frame) {
        try {
            AudioSample sample = new AudioSample(f);
            locoEngine.setSample(sample);
            coachEngine.setSample(sample); // Share sample
            locoPanel.setAudioSample(sample);
            coachPanel.setAudioSample(sample);

            label.setText(f.getName() + " (Loaded)");

            // Refresh table to show correct ms
            if (tableModel != null)
                tableModel.fireTableDataChanged();

        } catch (UnsupportedAudioFileException e) {
            JOptionPane.showMessageDialog(frame, "Format not supported! (Needs WAV): " + e.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Error loading file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Old updateNotchInfo method removed as TableModel handles this.

    private static void captureNotch(int index) {
        if (index < 0 || index >= notches.length)
            return;

        SpeedNotch n = notches[index];
        // Capture current speed as Cruise (User edits others)
        float currentSpeed = locoEngine.getSpeed();
        n.cruiseSpeed = currentSpeed;

        // Intelligent defaults if not set
        if (n.startSpeed == 0)
            n.startSpeed = Math.max(0, currentSpeed - 0.1f);
        if (n.endSpeed == 0)
            n.endSpeed = Math.min(1, currentSpeed + 0.1f);

        n.loopStart = (float) locoEngine.getLoopStart();
        n.loopEnd = (float) locoEngine.getLoopEnd();

        if (coachEngine != null) {
            n.coachLoopStart = (float) coachEngine.getLoopStart();
            n.coachLoopEnd = (float) coachEngine.getLoopEnd();
        }

        System.out.println("Captured Notch " + index);
    }

    private static void transitionToNotch(int index, JSlider speedSlider, JLabel speedLabel) {
        if (index < 0 || index >= notches.length)
            return;
        if (index == currentNotchIndex)
            return; // No change

        if (isTransitioning && transitionTimer != null && transitionTimer.isRunning()) {
            transitionTimer.stop();
        }

        isTransitioning = true;

        SpeedNotch current = notches[currentNotchIndex];
        SpeedNotch target = notches[index];

        boolean isUpshift = index > currentNotchIndex;

        // PHASE 1: EXIT CURRENT NOTCH
        // Upshift: Current.Speed -> Current.EndSpeed
        // Downshift: Current.Speed -> Current.StartSpeed

        float startSpeed1 = locoEngine.getSpeed();
        float targetSpeed1 = isUpshift ? current.endSpeed : current.startSpeed;

        // We use half the ramp time for exit, half for enter?
        // Or full ramp time for each? Let's use rampTime/2 for quicker responsiveness.
        float duration1 = current.rampTime / 2.0f;

        runRamp(startSpeed1, targetSpeed1, duration1, speedSlider, speedLabel, () -> {

            // PHASE 2: ENTER NEW NOTCH
            // Sync Table
            currentNotchIndex = index;
            if (notchTable != null)
                notchTable.setRowSelectionInterval(index, index);

            // Switch Loops
            locoEngine.setLoopPoints(target.loopStart, target.loopEnd);
            coachEngine.setLoopPoints(target.coachLoopStart, target.coachLoopEnd);

            // Upshift: Jump to Target.StartSpeed, Ramp to Target.CruiseSpeed
            // Downshift: Jump to Target.EndSpeed, Ramp to Target.CruiseSpeed

            float startSpeed2 = isUpshift ? target.startSpeed : target.endSpeed;
            float targetSpeed2 = target.cruiseSpeed;

            // Jump
            locoEngine.setSpeed(startSpeed2);
            coachEngine.setSpeed(startSpeed2);

            float duration2 = target.rampTime / 2.0f;

            runRamp(startSpeed2, targetSpeed2, duration2, speedSlider, speedLabel, () -> {
                isTransitioning = false;
                // Final snap
                locoEngine.setSpeed(targetSpeed2);
                coachEngine.setSpeed(targetSpeed2);
            });
        });
    }

    private static void runRamp(float startSpeed, float targetSpeed, float durationSec,
            JSlider speedSlider, JLabel speedLabel, Runnable onComplete) {
        int interval = 33; // ~30fps
        int calcSteps = (int) ((durationSec * 1000) / interval);
        if (calcSteps < 1)
            calcSteps = 1;
        final int steps = calcSteps;

        float speedStep = (targetSpeed - startSpeed) / steps;
        final int[] currentStep = { 0 };

        transitionTimer = new Timer(interval, e -> {
            currentStep[0]++;
            float newSpeed = startSpeed + (speedStep * currentStep[0]);

            // Clamp
            if (speedStep > 0 && newSpeed > targetSpeed)
                newSpeed = targetSpeed;
            if (speedStep < 0 && newSpeed < targetSpeed)
                newSpeed = targetSpeed;

            locoEngine.setSpeed(newSpeed);
            coachEngine.setSpeed(newSpeed);

            speedSlider.setValue((int) (newSpeed * 100));
            speedLabel.setText(String.format(" Master Speed: %d%%", (int) (newSpeed * 100)));

            if (currentStep[0] >= steps) {
                ((Timer) e.getSource()).stop();
                if (onComplete != null)
                    onComplete.run();
            }
        });
        transitionTimer.start();
    }

    private static void loadUrl(java.net.URL url, JLabel label, JFrame frame) {
        try {
            AudioSample sample = new AudioSample(url);
            locoEngine.setSample(sample);
            coachEngine.setSample(sample);

            locoPanel.setAudioSample(sample);
            coachPanel.setAudioSample(sample);

            label.setText(new File(url.getFile()).getName() + " (Resource Loaded)");

            // Refresh table
            if (tableModel != null)
                tableModel.fireTableDataChanged();

        } catch (UnsupportedAudioFileException e) {
            JOptionPane.showMessageDialog(frame, "Format not supported! (Needs WAV): " + e.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Error loading resource: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void audioLoop(AudioGenerator gen1, AudioGenerator gen2) {
        try {
            float sampleRate = 44100.0f;
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            gen1.setSampleRate(sampleRate);
            gen2.setSampleRate(sampleRate);

            int bufferSize = 4096;
            float[] mixBuffer = new float[bufferSize];
            float[] tempBuffer1 = new float[bufferSize];
            float[] tempBuffer2 = new float[bufferSize];
            byte[] byteBuffer = new byte[bufferSize * 2];

            while (running) {
                // Clear buffers
                for (int i = 0; i < bufferSize; i++) {
                    mixBuffer[i] = 0.0f;
                    tempBuffer1[i] = 0.0f;
                    tempBuffer2[i] = 0.0f;
                }

                // Read from engines
                gen1.read(tempBuffer1);
                gen2.read(tempBuffer2);

                // Mix
                for (int i = 0; i < bufferSize; i++) {
                    float mixed = tempBuffer1[i] + tempBuffer2[i];
                    // Soft Limiter
                    if (mixed > 1.0f)
                        mixed = 1.0f;
                    if (mixed < -1.0f)
                        mixed = -1.0f;

                    short s = (short) (mixed * 32767.0f);
                    byteBuffer[i * 2] = (byte) ((s >> 8) & 0xFF);
                    byteBuffer[i * 2 + 1] = (byte) (s & 0xFF);
                }
                line.write(byteBuffer, 0, byteBuffer.length);
            }
            line.drain();
            line.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    // --- Custom Waveform UI ---
    static class WaveformPanel extends JPanel {
        private AudioSample sample;
        private GrainEngine engine;

        // Selection
        private float loopStart = 0.0f;
        private float loopEnd = 1.0f;

        // Dragging state
        private float dragAnchor = 0.0f;

        public WaveformPanel(GrainEngine engine) {
            this.engine = engine;
            this.setBackground(Color.BLACK);
            this.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

            // Mouse Interaction
            java.awt.event.MouseAdapter input = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (sample == null)
                        return;
                    float xWith = (float) getWidth();
                    dragAnchor = Math.max(0.0f, Math.min(1.0f, e.getX() / xWith));

                    // On click without drag yet, reset region to point? Or start selection?
                    loopStart = dragAnchor;
                    loopEnd = dragAnchor;

                    updateEngine();
                    repaint();
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (sample == null)
                        return;
                    float xWith = (float) getWidth();
                    float currentPos = Math.max(0.0f, Math.min(1.0f, e.getX() / xWith));

                    if (currentPos < dragAnchor) {
                        loopStart = currentPos;
                        loopEnd = dragAnchor;
                    } else {
                        loopStart = dragAnchor;
                        loopEnd = currentPos;
                    }

                    updateEngine();
                    repaint();
                }
            };

            this.addMouseListener(input);
            this.addMouseMotionListener(input);
        }

        public void setAudioSample(AudioSample s) {
            this.sample = s;
            this.loopStart = 0.0f;
            this.loopEnd = 1.0f;
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

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (sample == null) {
                g.setColor(Color.WHITE);
                g.drawString("Load a WAV file to see the waveform...", 20, 30);
                return;
            }

            // Bg
            g.setColor(new Color(20, 20, 20));
            g.fillRect(0, 0, getWidth(), getHeight());

            // 1. Draw Waveform
            g.setColor(new Color(0, 255, 100)); // Bright green
            int w = getWidth();
            int h = getHeight();
            int h2 = h / 2;

            int sampleLen = sample.getLength();
            int step = Math.max(1, sampleLen / w); // Downsample for display

            for (int x = 0; x < w; x++) {
                int index = x * step;
                if (index >= sampleLen)
                    break;

                // Get peak in this chunk
                float maxVal = -1.0f;
                float minVal = 1.0f;
                // Scan a few samples to find peak, prevents aliasing disappearance
                int scanSize = Math.min(step, 100);
                for (int k = 0; k < scanSize && (index + k) < sampleLen; k++) {
                    float v = sample.getSample(index + k);
                    if (v > maxVal)
                        maxVal = v;
                    if (v < minVal)
                        minVal = v;
                }

                if (Math.abs(maxVal - minVal) < 0.01) {
                    // Draw at least a dot
                    int y = (int) (h2 - (sample.getSample(index) * h2));
                    g.drawLine(x, y, x, y);
                } else {
                    int y1 = (int) (h2 - (maxVal * h2));
                    int y2 = (int) (h2 - (minVal * h2));
                    g.drawLine(x, y1, x, y2);
                }
            }

            // 2. Draw Loop Overlay (The NON-selected parts are dimmed)
            int x1 = (int) (loopStart * w);
            int x2 = (int) (loopEnd * w);

            // Dim region outside loop
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, x1, h); // Left dim
            g.fillRect(x2, 0, w - x2, h); // Right dim

            // Loop Borders
            g.setColor(Color.RED);
            g.drawLine(x1, 0, x1, h);
            g.drawLine(x2, 0, x2, h);

            // Text
            g.setColor(Color.WHITE);
            float totalMs = (sample.getLength() / sample.getSampleRate()) * 1000;
            int startMs = (int) (loopStart * totalMs);
            int endMs = (int) (loopEnd * totalMs);

            g.drawString(String.format("START (%d ms)", startMs), x1 + 5, 20);
            g.drawString(String.format("END (%d ms)", endMs), x2 - 80, h - 10);

            // Highlight active region slightly?
            g.setColor(new Color(255, 0, 0, 30));
            g.fillRect(x1, 0, x2 - x1, h);

            // 3. Draw Playhead
            if (engine != null) {
                float pos = engine.getPositionNormalized();
                int xPlayhead = (int) (pos * w);
                g.setColor(Color.CYAN);
                g.drawLine(xPlayhead, 0, xPlayhead, h);
            }
        }
    }
}
