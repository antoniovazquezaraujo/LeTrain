package letrain.lab;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import letrain.ground.GroundMap;
import letrain.ground.ZoneSensor;

/**
 * Swing lab for ADR-025: renders a procedurally generated map and, as the mouse moves, shows the
 * zone weights the sensor would produce (primary plus influence per zone) and the time it takes.
 * Mouse wheel zooms; right-button drag pans.
 */
public class ZoneSensorLab {

    private static final int TILES_W = 420;
    private static final int TILES_H = 300;
    private static final int PANEL_W = 840;
    private static final int PANEL_H = 600;
    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 10;
    private static final long COMPUTE_INTERVAL_MS = 40;

    private final MapPanel mapPanel = new MapPanel();
    private final JLabel status = new JLabel(" ");
    private final JPanel zonePanel = new JPanel();
    private final JTextField seedField = new JTextField("1", 8);
    private final JTextField centerXField = new JTextField("0", 6);
    private final JTextField centerYField = new JTextField("0", 6);
    private final JLabel timing = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("zoom 2 px/tile");

    private GroundMap map;
    private int[][] terrain;
    private int originX = -TILES_W / 2;
    private int originY = -TILES_H / 2;
    private double viewCenterX;
    private double viewCenterY;
    private int tile = 2;
    private ZoneSensor sensor = new ZoneSensor(12, 1);
    private boolean useCache;
    private int cursorX;
    private int cursorY;
    private long lastCompute;
    private BufferedImage image;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ZoneSensorLab().show());
    }

    private void show() {
        JFrame frame = new JFrame("Zone sensor lab (ADR-025)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.add(buildControls(), BorderLayout.NORTH);
        frame.add(mapPanel, BorderLayout.CENTER);
        frame.add(buildResults(), BorderLayout.EAST);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        generate();
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(new JLabel("seed"));
        controls.add(seedField);
        controls.add(new JLabel("center x"));
        controls.add(centerXField);
        controls.add(new JLabel("y"));
        controls.add(centerYField);
        JButton generate = new JButton("Generate");
        generate.addActionListener(e -> generate());
        controls.add(generate);

        JButton zoomOut = new JButton("−");
        zoomOut.addActionListener(e -> zoomBy(1, PANEL_W / 2, PANEL_H / 2));
        controls.add(zoomOut);
        JButton zoomIn = new JButton("+");
        zoomIn.addActionListener(e -> zoomBy(-1, PANEL_W / 2, PANEL_H / 2));
        controls.add(zoomIn);
        controls.add(zoomLabel);

        JSlider radius = new JSlider(2, 30, sensor.radius());
        radius.setPreferredSize(new Dimension(140, 24));
        JLabel radiusLabel = new JLabel("R " + sensor.radius());
        radius.addChangeListener(e -> {
            sensor = new ZoneSensor(radius.getValue(), sensor.stride());
            radiusLabel.setText("R " + sensor.radius());
            compute(cursorX, cursorY, true);
        });
        controls.add(radiusLabel);
        controls.add(radius);

        JSlider stride = new JSlider(1, 4, sensor.stride());
        stride.setPreferredSize(new Dimension(90, 24));
        JLabel strideLabel = new JLabel("stride " + sensor.stride());
        stride.addChangeListener(e -> {
            sensor = new ZoneSensor(sensor.radius(), stride.getValue());
            strideLabel.setText("stride " + sensor.stride());
            compute(cursorX, cursorY, true);
        });
        controls.add(strideLabel);
        controls.add(stride);

        JCheckBox cache = new JCheckBox("cached terrain", false);
        cache.addChangeListener(e -> {
            useCache = cache.isSelected();
            compute(cursorX, cursorY, true);
        });
        controls.add(cache);
        controls.add(timing);
        return controls;
    }

    private JPanel buildResults() {
        JPanel results = new JPanel();
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        results.add(new JLabel("zone"));
        results.add(new JLabel("influence (bar) · weight (number)"));
        zonePanel.setLayout(new BoxLayout(zonePanel, BoxLayout.Y_AXIS));
        results.add(zonePanel);
        return results;
    }

    private void generate() {
        int seed = parseInt(seedField.getText(), 1);
        int centerX = parseInt(centerXField.getText(), 0);
        int centerY = parseInt(centerYField.getText(), 0);
        originX = centerX - TILES_W / 2;
        originY = centerY - TILES_H / 2;
        viewCenterX = centerX;
        viewCenterY = centerY;
        clampView();
        map = new letrain.ground.impl.GroundMap(seed, null);
        status.setText("generating " + TILES_W + "x" + TILES_H + " tiles…");
        new SwingWorker<int[][], Void>() {
            @Override
            protected int[][] doInBackground() {
                int[][] cells = new int[TILES_H][TILES_W];
                for (int y = 0; y < TILES_H; y++) {
                    for (int x = 0; x < TILES_W; x++) {
                        Integer value = map.getValueAt(originX + x, originY + y);
                        cells[y][x] = value == null ? -1 : value;
                    }
                }
                return cells;
            }

            @Override
            protected void done() {
                try {
                    terrain = get();
                } catch (Exception e) {
                    status.setText("generation failed: " + e.getMessage());
                    return;
                }
                image = renderImage(terrain);
                mapPanel.repaint();
                compute(cursorX, cursorY, true);
                status.setText(summary(terrain));
            }
        }.execute();
    }

    private String summary(int[][] cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int[] row : cells) {
            for (int value : row) {
                String zone = ZoneSensor.zoneOf(value);
                if (zone != null) {
                    counts.merge(zone, 1, Integer::sum);
                }
            }
        }
        StringBuilder text = new StringBuilder("tiles: ");
        counts.forEach((zone, count) -> text.append(zone).append(' ').append(count).append("  "));
        return text.toString();
    }

    private BufferedImage renderImage(int[][] cells) {
        BufferedImage rendered = new BufferedImage(TILES_W, TILES_H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TILES_H; y++) {
            for (int x = 0; x < TILES_W; x++) {
                rendered.setRGB(x, y, colorOf(cells[y][x]).getRGB());
            }
        }
        return rendered;
    }

    private Color colorOf(int terrain) {
        return switch (terrain) {
            case GroundMap.WATER -> new Color(30, 136, 229);
            case GroundMap.ROCK -> new Color(117, 117, 117);
            case GroundMap.GROUND -> new Color(124, 179, 66);
            case GroundMap.GOLD_MINE -> new Color(249, 168, 37);
            case GroundMap.MINE -> new Color(109, 76, 65);
            case GroundMap.RUBY_MINE -> new Color(198, 40, 40);
            case GroundMap.JEWELRY_STORE -> new Color(255, 241, 118);
            case GroundMap.POWER_PLANT -> new Color(161, 136, 127);
            case GroundMap.RUBY_STORE -> new Color(239, 154, 154);
            default -> new Color(33, 33, 33);
        };
    }

    private void zoomBy(int delta, int anchorX, int anchorY) {
        int next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, tile - delta));
        if (next == tile) {
            return;
        }
        double worldX = viewCenterX + (anchorX - PANEL_W / 2.0) / tile;
        double worldY = viewCenterY + (anchorY - PANEL_H / 2.0) / tile;
        tile = next;
        viewCenterX = worldX - (anchorX - PANEL_W / 2.0) / tile;
        viewCenterY = worldY - (anchorY - PANEL_H / 2.0) / tile;
        clampView();
        zoomLabel.setText("zoom " + tile + " px/tile");
        mapPanel.repaint();
        compute(cursorX, cursorY, true);
    }

    private void panBy(double screenDx, double screenDy) {
        viewCenterX -= screenDx / tile;
        viewCenterY -= screenDy / tile;
        clampView();
        mapPanel.repaint();
    }

    private void clampView() {
        double halfW = PANEL_W / (2.0 * tile);
        double halfH = PANEL_H / (2.0 * tile);
        viewCenterX = Math.max(originX + halfW, Math.min(originX + TILES_W - halfW, viewCenterX));
        viewCenterY = Math.max(originY + halfH, Math.min(originY + TILES_H - halfH, viewCenterY));
    }

    private void compute(int tileX, int tileY, boolean force) {
        if (terrain == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastCompute < COMPUTE_INTERVAL_MS) {
            return;
        }
        lastCompute = now;
        long start = System.nanoTime();
        ZoneSensor.Result result;
        try {
            result = sensor.sense(this::terrainAt, tileX, tileY);
        } catch (RuntimeException e) {
            status.setText("compute failed: " + e.getMessage());
            return;
        }
        long micros = (System.nanoTime() - start) / 1000;
        updateZonePanel(result);
        timing.setText(String.format("focus (%d, %d) · %.0f µs", tileX, tileY, (double) micros));
    }

    private int terrainAt(int x, int y) {
        if (useCache) {
            int localX = x - originX;
            int localY = y - originY;
            if (localY < 0 || localY >= terrain.length || localX < 0
                    || localX >= terrain[0].length) {
                return -1;
            }
            return terrain[localY][localX];
        }
        Integer value = map.getValueAt(x, y);
        return value == null ? -1 : value;
    }

    private void updateZonePanel(ZoneSensor.Result result) {
        zonePanel.removeAll();
        for (Map.Entry<String, Float> entry : result.influence().entrySet()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JLabel name = new JLabel(String.format("%-14s", entry.getKey()));
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setPreferredSize(new Dimension(140, 14));
            bar.setValue(Math.round(entry.getValue() * 100));
            JLabel percent = new JLabel(Math.round(entry.getValue() * 100) + "%");
            JLabel weight = new JLabel(String.format("→ %.2f", result.weightOf(entry.getKey())));
            row.add(name);
            row.add(bar);
            row.add(percent);
            row.add(weight);
            zonePanel.add(row);
        }
        zonePanel.add(new JLabel("primary: " + result.primary()));
        zonePanel.revalidate();
        zonePanel.repaint();
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private class MapPanel extends JPanel {

        private int lastDragX;
        private int lastDragY;
        private boolean dragging;

        MapPanel() {
            setPreferredSize(new Dimension(PANEL_W, PANEL_H));
            setBackground(new Color(33, 33, 33));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateFocus(e);
                    mapPanel.repaint();
                    compute(cursorX, cursorY, false);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging) {
                        return;
                    }
                    panBy(e.getX() - lastDragX, e.getY() - lastDragY);
                    lastDragX = e.getX();
                    lastDragY = e.getY();
                    updateFocus(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        dragging = true;
                        lastDragX = e.getX();
                        lastDragY = e.getY();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                }

                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    zoomBy(e.getWheelRotation(), e.getX(), e.getY());
                }
            };
            addMouseMotionListener(mouse);
            addMouseListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void updateFocus(MouseEvent e) {
            cursorX = (int) Math.floor(viewCenterX + (e.getX() - PANEL_W / 2.0) / tile);
            cursorY = (int) Math.floor(viewCenterY + (e.getY() - PANEL_H / 2.0) / tile);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int destX = (int) Math.round(PANEL_W / 2.0 - (viewCenterX - originX) * tile);
            int destY = (int) Math.round(PANEL_H / 2.0 - (viewCenterY - originY) * tile);
            g2.drawImage(image, destX, destY, TILES_W * tile, TILES_H * tile, null);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int screenX =
                    (int) Math.round(PANEL_W / 2.0 + (cursorX - viewCenterX) * tile + tile / 2.0);
            int screenY =
                    (int) Math.round(PANEL_H / 2.0 + (cursorY - viewCenterY) * tile + tile / 2.0);
            int radiusPx = sensor.radius() * tile;
            g2.setColor(Color.WHITE);
            g2.drawOval(screenX - radiusPx, screenY - radiusPx, radiusPx * 2, radiusPx * 2);
            g2.drawLine(screenX - 6, screenY, screenX + 6, screenY);
            g2.drawLine(screenX, screenY - 6, screenX, screenY + 6);
        }
    }
}
