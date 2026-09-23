package letrain.visitor.terminal;

import com.googlecode.lanterna.TextColor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.impl.terminal.TerminalView;
import letrain.palette.VisualPalette;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;

import letrain.vehicle.Cursor;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderVisitor implements Visitor {
    Logger log = LoggerFactory.getLogger(RenderVisitor.class);
    private Model model;
    private final TerminalPalette palette;
    private TerminalPalette.Resolved paletteColors;
    /** Paleta diurna de referencia: el faro mezcla hacia ella las celdas que ilumina. */
    private final TerminalPalette.Resolved dayColors;
    private float paletteBand = -1f;
    /** Celdas iluminadas por los faros de las locomotoras y con qué fuerza (0..1). */
    private final Map<Long, Float> litCells = new HashMap<>();

    public static final char[] CRASH_ASPECTS =
            {'⁖', '⁘', '⁙', '⁚', '⁛', '⁝', '⁞', '․', '‥', '…', '⋯', '⋰', '⋱'};

    public static String TUNNEL_RAILTRACK_ASPECT = ".";
    public static String GROUND_ASPECT = " ";
    public static String WATER_ASPECT = "~";
    public static String ROCK_ASPECT = "*";
    public static String TUNNEL_GATE_RAILTRACK_ASPECT = "⋂";
    public static String BRIDGE_RAILTRACK_ASPECT = "\u252C";
    public static String BRIDGE_GATE_RAILTRACK_ASPECT = "\u224E";
    public static String SENSOR_ASPECT = "₪";
    public static String GENERIC_STATION_ASPECT = "◇";
    public static String LOAD_STATION_ASPECT = "▲";
    public static String UNLOAD_STATION_ASPECT = "▼";
    public static String RAIL_CROSS_ASPECT = "┼";
    public static String DIAGONAL_RAIL_CROSS_ASPECT = "╳";
    public static String MIXED_RAIL_CROSS_ASPECT = "*";
    public static String SEMAPHORE_ASPECT = ":";
    public static String CURVE_RAIL_TRACK_ASPECT = "·";

    public static String CURSOR_ASPECT_E = ">";
    public static String CURSOR_ASPECT_W = "<";
    public static String CURSOR_ASPECT_NE = "⌝";
    public static String CURSOR_ASPECT_SW = "⌞";
    public static String CURSOR_ASPECT_N = "⌃";
    public static String CURSOR_ASPECT_S = "⌄";
    public static String CURSOR_ASPECT_NW = "⌜";
    public static String CURSOR_ASPECT_SE = "⌟";
    public static String HORIZONTAL_DIR = "─";
    public static String VERTICAL_DIR = "│";
    public static String DIAGONAL_DIR = "╱";
    public static String ANTI_DIAGONAL_DIR = "╲";
    public static String PRODUCER_ASPECT = "●";
    public static String CONSUMER_ASPECT = "◌";
    public static String DEAD_END_ASPECT = "╺";

    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    Station selectedStation;
    RailSemaphore selectedSemaphore;
    letrain.track.SpeedSignal selectedSpeedSignal;
    private final TerminalView view;
    private GameMode mode;
    boolean showId = false;

    public RenderVisitor(TerminalView view) {
        this(view, TerminalPalette.detect());
    }

    /** Constructor de test: permite fijar el modo de color de la paleta. */
    RenderVisitor(TerminalView view, TerminalPalette palette) {
        this.view = view;
        this.palette = palette;
        this.paletteColors = palette.resolve(0f);
        this.dayColors = this.paletteColors;
        resetColors();
    }

    private TextColor color(TerminalPalette.Token token) {
        return paletteColors.color(token);
    }

    private int rgb(TerminalPalette.Token token) {
        return paletteColors.rgb(token);
    }

    /** Recalcula las celdas que iluminan los faros; de día no hay haz. */
    private void updateHeadlights(float dayNightRatio) {
        litCells.clear();
        if (VisualPalette.lightsOnFactor(dayNightRatio) <= 0f || model.getLocomotives() == null) {
            return;
        }
        for (Locomotive locomotive : model.getLocomotives()) {
            addHeadlight(locomotive);
        }
    }

    private void addHeadlight(Locomotive locomotive) {
        if (locomotive.getDir() == null || !locomotive.isEngineOn()
                || isHiddenInTunnel(locomotive)) {
            return;
        }
        int cx = locomotive.getPosition().getX();
        int cy = locomotive.getPosition().getY();
        for (int dx = -Headlight.RADIUS; dx <= Headlight.RADIUS; dx++) {
            for (int dy = -Headlight.RADIUS; dy <= Headlight.RADIUS; dy++) {
                float factor = Headlight.factor(dx, dy, locomotive.getDir());
                if (factor <= 0f) {
                    continue;
                }
                if (isHiddenTunnelCell(cx + dx, cy + dy)) {
                    continue;
                }
                long key = cellKey(cx + dx, cy + dy);
                Float previous = litCells.get(key);
                if (previous == null || factor > previous) {
                    litCells.put(key, factor);
                }
            }
        }
    }

    /**
     * A train inside a tunnel disappears from the map outside Rails mode; its light must vanish
     * with it.
     */
    private boolean isHiddenInTunnel(Locomotive locomotive) {
        return this.mode != GameMode.RAILS && locomotive.getTrack() instanceof RailTrack
                && ((RailTrack) locomotive.getTrack())
                        .getVisualType() == RailTrack.VisualType.TUNNEL;
    }

    /** The same rule per cell: a hidden tunnel swallows the beam instead of lighting it up. */
    private boolean isHiddenTunnelCell(int x, int y) {
        if (this.mode == GameMode.RAILS || model.getRailMap() == null) {
            return false;
        }
        RailTrack track = model.getRailMap().getTrackAt(x, y);
        return track != null && track.getVisualType() == RailTrack.VisualType.TUNNEL;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Fuerza del faro en la celda, o null si está a oscuras. */
    private Float litFactor(int x, int y) {
        if (paletteBand <= 0f) {
            return null;
        }
        return litCells.get(cellKey(x, y));
    }

    /** Enciende el fondo de la celda: papel nocturno mezclado hacia el diurno. */
    private void applyHeadlightBackground(int x, int y) {
        Float lit = litFactor(x, y);
        if (lit == null || lit <= 0f) {
            return;
        }
        int board = TerminalPalette.mix(rgb(TerminalPalette.Token.BOARD),
                dayColors.rgb(TerminalPalette.Token.BOARD), lit * Headlight.MAX_LIGHT);
        view.setBgColor(palette.colorOf(board));
    }

    /** Color del token con el faro aplicado: mezcla su versión nocturna con la diurna. */
    private TextColor headlightColor(TerminalPalette.Token token, int x, int y) {
        Float lit = litFactor(x, y);
        if (lit == null || lit <= 0f) {
            return color(token);
        }
        int value =
                TerminalPalette.mix(rgb(token), dayColors.rgb(token), lit * Headlight.MAX_LIGHT);
        return palette.colorOf(value);
    }

    boolean isShowId() {
        return showId;
    }

    public void resetColors() {
        view.setFgColor(color(TerminalPalette.Token.LABEL));
        view.setBgColor(color(TerminalPalette.Token.BOARD));
        view.setUnderline(false);
    }

    @Override
    public void visitModel(Model model) {
        this.model = model;
        if (model == null) {
            return;
        }
        float dayNightRatio =
                model.getGameClock() == null ? 0f : model.getGameClock().getDayNightRatio();
        float band = TerminalPalette.band(dayNightRatio, paletteBand);
        if (band != paletteBand) {
            paletteBand = band;
            paletteColors = palette.resolve(band);
        }
        this.showId = model.isShowId();
        this.mode = model.getMode();
        updateHeadlights(dayNightRatio);
        selectedLocomotive = model.getSelectedLocomotive();
        selectedFork = model.getSelectedFork();
        selectedStation = model.getSelectedStation();
        selectedSemaphore = model.getSelectedSemaphore();
        selectedSpeedSignal = model.getSelectedSpeedSignal();
        if (model.getGroundMap() != null) {
            model.getGroundMap().accept(this);
        }
        if (model.getRailMap() != null) {
            model.getRailMap().accept(this);
        }
        if (model.getSensors() != null) {
            model.getSensors().forEach(t -> t.accept(this));
        }
        if (model.getForks() != null) {
            model.getForks().forEach(t -> t.accept(this));
        }
        if (model.getSemaphores() != null) {
            model.getSemaphores().forEach(t -> t.accept(this));
        }
        if (model.getWagons() != null) {
            model.getWagons().forEach(t -> t.accept(this));
        }
        if (model.getLocomotives() != null) {
            model.getLocomotives().forEach(t -> t.accept(this));
        }
        if (model.getStations() != null) {
            model.getStations().forEach(t -> t.accept(this));
        }
        if (model.getCursor() != null) {
            visitCursor(model.getCursor());
        }
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        letrain.track.rail.RailTrack.VisualType vt = track.getVisualType();
        if (vt == letrain.track.rail.RailTrack.VisualType.TUNNEL && this.mode != GameMode.RAILS) {
            return;
        }
        TextColor blockedColor = getTrackBlockedColor(track);
        String aspect = getTrackAspect(track);
        int x = track.getPosition().getX();
        int y = track.getPosition().getY();

        if (blockedColor != null) {
            view.setFgColor(blockedColor);
        } else if (track.getComponent() instanceof letrain.track.Sensor) {
            if (track.getComponent() instanceof Station) {
                view.setFgColor(headlightColor(TerminalPalette.Token.STATION, x, y));
            } else {
                view.setFgColor(headlightColor(TerminalPalette.Token.SENSOR, x, y));
            }
        } else if (DEAD_END_ASPECT.equals(aspect)) {
            view.setFgColor(headlightColor(TerminalPalette.Token.DEAD_END, x, y));
        } else {
            view.setFgColor(headlightColor(TerminalPalette.Token.RAIL, x, y));
        }
        applyHeadlightBackground(x, y);
        view.set(x, y, aspect);
        resetColors();
    }

    private Point getRightSide(Point pos, letrain.map.Dir dir) {
        if (dir == null) {
            return new Point(pos.getX() + 1, pos.getY());
        }
        int val = dir.getValue();
        letrain.map.Dir rightDir = letrain.map.Dir.fromInt(val - 2);
        Point newPos = new Point(pos);
        newPos.move(rightDir);
        return newPos;
    }

    private boolean isStationActive(Station station) {
        if (model == null) {
            return false;
        }
        for (letrain.vehicle.rail.impl.Locomotive loco : model.getLocomotives()) {
            if (loco.getTrain() != null && loco.getTrain().getStationId() == station.getId()
                    && loco.getTrain().getLogisticsManager().isLoading()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitStation(Station station) {
        Track track = station.getTrack();
        Point renderPos = getRightSide(track.getPosition(), station.getCreationDir());

        if (station.getCargoType() != letrain.track.CargoTypes.NONE) {
            boolean isProducer = station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER;
            view.setFgColor(getCargoColor(station.getCargoType(), isProducer));
        } else {
            view.setFgColor(color(TerminalPalette.Token.STATION));
        }

        String aspect = GENERIC_STATION_ASPECT;
        if (station.getCargoType() != letrain.track.CargoTypes.NONE) {
            boolean isProducer = station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER;
            if (isProducer) {
                aspect = LOAD_STATION_ASPECT;
            } else {
                aspect = UNLOAD_STATION_ASPECT;
            }
        }
        if (isStationActive(station)) {
            boolean blinkState = (System.currentTimeMillis() / 300) % 2 == 0;
            if (!blinkState) {
                resetColors();
                return;
            }
        }

        view.set(renderPos.getX(), renderPos.getY(), aspect);

        if (this.mode == GameMode.STATIONS) {
            if (station == selectedStation) {
                view.setUnderline(true);
                view.setFgColor(color(TerminalPalette.Token.HIGHLIGHT));
            } else {
                view.setFgColor(color(TerminalPalette.Token.LABEL));
            }
            view.set(renderPos.getX() + 1, renderPos.getY(), String.valueOf(station.getId()));
            view.setUnderline(false);
        }
        resetColors();
    }

    @Override
    public void visitSensor(Sensor sensor) {
        Track track = sensor.getTrack();
        if (track.getComponent() instanceof Station) {
            return;
        }
        Point renderPos = getRightSide(track.getPosition(), sensor.getCreationDir());

        view.setFgColor(color(TerminalPalette.Token.SENSOR));
        view.set(renderPos.getX(), renderPos.getY(), SENSOR_ASPECT);

        if (this.mode == GameMode.SENSORS) {
            if (sensor.getId() == (model.getSelectedSensor() != null
                    ? model.getSelectedSensor().getId()
                    : -1)) {
                view.setUnderline(true);
                view.setFgColor(color(TerminalPalette.Token.HIGHLIGHT));
            } else {
                view.setFgColor(color(TerminalPalette.Token.LABEL));
            }
            String arrow = speedSignalArrow(sensor.getCreationDir());
            int step = labelStep(renderPos, sensor.getTrack().getPosition());
            view.set(renderPos.getX() + step, renderPos.getY(), arrow);
            view.set(renderPos.getX() + 2 * step, renderPos.getY(), String.valueOf(sensor.getId()));
            view.setUnderline(false);
        }
        resetColors();
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        Point renderPos = getRightSide(semaphore.getPosition(), semaphore.getCreationDir());

        if (semaphore.isOpen()) {
            view.setFgColor(color(TerminalPalette.Token.SEMAPHORE_OPEN));
        } else {
            view.setFgColor(color(TerminalPalette.Token.SEMAPHORE_CLOSED));
        }
        view.set(renderPos.getX(), renderPos.getY(), SEMAPHORE_ASPECT);

        if (mode == GameMode.SEMAPHORES) {
            if (semaphore == selectedSemaphore) {
                view.setUnderline(true);
                view.setFgColor(color(TerminalPalette.Token.HIGHLIGHT));
            } else {
                view.setFgColor(color(TerminalPalette.Token.LABEL));
            }
            String arrow = speedSignalArrow(semaphore.getCreationDir());
            int step = labelStep(renderPos, semaphore.getPosition());
            view.set(renderPos.getX() + step, renderPos.getY(), arrow);
            view.set(renderPos.getX() + 2 * step, renderPos.getY(),
                    String.valueOf(semaphore.getId()));
            view.setUnderline(false);
        }
        resetColors();
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {
        letrain.map.Point renderPos =
                getRightSide(speedSignal.getPosition(), speedSignal.getCreationDir());

        if (speedSignal.isMax()) {
            view.setFgColor(color(TerminalPalette.Token.SIGNAL_MAX));
        } else {
            view.setFgColor(color(TerminalPalette.Token.SIGNAL_MIN));
        }

        int limit = speedSignal.getLimit();
        char icon;
        if (limit >= 1 && limit <= 10) {
            icon = (char) ('\u245F' + limit);
        } else {
            icon = '?';
        }

        view.set(renderPos.getX(), renderPos.getY(), String.valueOf(icon));

        if (mode == GameMode.SPEED_SIGNALS) {
            if (speedSignal == selectedSpeedSignal) {
                view.setUnderline(true);
                view.setFgColor(color(TerminalPalette.Token.HIGHLIGHT));
            } else {
                view.setFgColor(color(TerminalPalette.Token.LABEL));
            }
            String arrow = speedSignalArrow(speedSignal.getCreationDir());
            int step = labelStep(renderPos, speedSignal.getPosition());
            view.set(renderPos.getX() + step, renderPos.getY(), arrow);
            view.set(renderPos.getX() + 2 * step, renderPos.getY(),
                    String.valueOf(speedSignal.getId()));
            view.setUnderline(false);
        }
        resetColors();
    }

    /**
     * Column step for the arrow/ID drawn next to an element icon: to the outer side, so a signal on
     * the west side does not paint its label over the rails.
     */
    private int labelStep(Point renderPos, Point trackPos) {
        if (trackPos == null) {
            return 1;
        }
        return renderPos.getX() < trackPos.getX() ? -1 : 1;
    }

    private String speedSignalArrow(letrain.map.Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
                return "→";
            case W:
                return "←";
            case N:
                return "↑";
            case S:
                return "↓";
            case NE:
                return "↗";
            case SW:
                return "↙";
            case NW:
                return "↖";
            case SE:
                return "↘";
        }
        return "";
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        TextColor blockedColor = getTrackBlockedColor(track);
        if (blockedColor != null) {
            view.setFgColor(blockedColor);
        } else {
            view.setFgColor(color(TerminalPalette.Token.FORK));
        }

        view.set(track.getPosition().getX(), track.getPosition().getY(),
                dirGraphicAspect(track.getFirstOpenDir()));

        if (this.mode == GameMode.FORKS) {
            if (track == selectedFork) {
                view.setUnderline(true);
                view.setFgColor(color(TerminalPalette.Token.HIGHLIGHT));
            } else {
                view.setFgColor(color(TerminalPalette.Token.LABEL));
            }
            view.set(track.getPosition().getX() + 1, track.getPosition().getY(),
                    String.valueOf(track.getId()));
            view.setUnderline(false);
        }
        resetColors();
    }

    private void highlightIfSelected(Linker linker) {
        if (selectedLocomotive != null && selectedLocomotive.getTrain() != null) {
            Train activeTrain = selectedLocomotive.getTrain();
            boolean highlighted = false;
            if (activeTrain.getLinkersToRemove().contains(linker)) {
                highlighted = true;
            } else {
                int count = 0;
                for (Linker linkerToJoin : activeTrain.getLinkersToJoin()) {
                    if (count >= activeTrain.getNumLinkersToJoin()) {
                        break;
                    }
                    if (linkerToJoin == linker) {
                        highlighted = true;
                        break;
                    }
                    count++;
                }
            }

            if (highlighted) {
                view.setBgColor(color(TerminalPalette.Token.SELECTION_LINK));
                view.setFgColor(TextColor.ANSI.BLACK);
            }
        }
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        if (locomotive.getTrack() instanceof RailTrack
                && (((RailTrack) locomotive.getTrack())
                        .getVisualType() == RailTrack.VisualType.TUNNEL)
                && this.mode != GameMode.RAILS) {
            return;
        }
        if (locomotive.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(),
                    getCrashAspect());
            resetColors();
            return;
        }
        TextColor locoColor = parseColor(locomotive.getColor());
        view.setFgColor(locoColor != null ? locoColor : color(TerminalPalette.Token.LOCO));
        applyHeadlightBackground(locomotive.getPosition().getX(), locomotive.getPosition().getY());
        if (locomotive == selectedLocomotive) {
            view.setUnderline(true);
        }
        highlightIfSelected(locomotive);
        if (locomotive.isShowingDir()) {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(),
                    cursorGraphicAspect(locomotive.getDir()));
        } else {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(),
                    locomotive.getAspect());
            view.set(locomotive.getPosition().getX() + 1, locomotive.getPosition().getY(),
                    (isShowId() ? ("" + locomotive.getId()) : ""));
        }
        resetColors();
    }

    @Override
    public void visitWagon(Wagon wagon) {
        if (wagon.getTrack() instanceof RailTrack
                && (((RailTrack) wagon.getTrack()).getVisualType() == RailTrack.VisualType.TUNNEL)
                && this.mode != GameMode.RAILS) {
            return;
        }
        if (wagon.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), getCrashAspect());
            return;
        }
        if (wagon.getExclusiveCargoType() != letrain.track.CargoTypes.NONE) {
            boolean isLoaded = wagon.getCargoAmount() > 0;
            view.setFgColor(getCargoColor(wagon.getExclusiveCargoType(), true));
            if (isLoaded) {
                boolean isLoadingProcess = wagon.getTrain() != null
                        && wagon.getTrain().getLogisticsManager().isLoading();
                if (isLoadingProcess && !wagon.isFull()) {
                    // Lanterna's Swing emulator often ignores SGR.BLINK. We manually toggle
                    // underline every 500ms.
                    boolean blinkState = (System.currentTimeMillis() / 300) % 2 == 0;
                    view.setUnderline(blinkState);
                } else {
                    view.setUnderline(true);
                }
            } else {
                view.setUnderline(false);
            }
        } else {
            view.setFgColor(color(TerminalPalette.Token.WAGON));
        }
        applyHeadlightBackground(wagon.getPosition().getX(), wagon.getPosition().getY());
        highlightIfSelected(wagon);
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
        resetColors();
    }

    @Override
    public void visitCursor(Cursor cursor) {
        String aspect = cursorGraphicAspect(cursor.getDir());
        switch (cursor.getMode()) {
            case DRAWING:
                view.setFgColor(color(TerminalPalette.Token.CURSOR_DRAWING));
                break;
            case MAKING_TRACKS:
                // Draw braille on the tile being constructed (previous position)
                letrain.map.Point cp = cursor.getConstructionPosition();
                if (cp != null) {
                    String[] braille = {"⡀", "⢀", "⡄", "⣤", "⣦", "⣶", "⣾", "⣿"};
                    int idx2 = (int) (cursor.getProgress() * (braille.length - 1));
                    String brailleChar = braille[Math.max(0, Math.min(idx2, braille.length - 1))];
                    view.setFgColor(color(TerminalPalette.Token.CURSOR_MOVING));
                    view.set(cp.getX(), cp.getY(), brailleChar);
                    resetColors();
                }
                // Draw cursor normally on its own position
                view.setFgColor(color(TerminalPalette.Token.CURSOR_DRAWING));
                break;
            case ERASING:
                view.setFgColor(color(TerminalPalette.Token.CURSOR_ERASING));
                break;
            case MOVING:
                view.setFgColor(color(TerminalPalette.Token.CURSOR_MOVING));
                break;
        }
        applyHeadlightBackground(cursor.getPosition().getX(), cursor.getPosition().getY());
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), aspect);
        resetColors();
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track instanceof letrain.track.rail.RailTrack) {
            letrain.track.rail.RailTrack.VisualType vt =
                    ((letrain.track.rail.RailTrack) track).getVisualType();
            if (vt == letrain.track.rail.RailTrack.VisualType.TUNNEL) {
                return TUNNEL_RAILTRACK_ASPECT;
            } else if (vt == letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE) {
                return TUNNEL_GATE_RAILTRACK_ASPECT;
            } else if (vt == letrain.track.rail.RailTrack.VisualType.BRIDGE) {
                return BRIDGE_RAILTRACK_ASPECT;
            } else if (vt == letrain.track.rail.RailTrack.VisualType.BRIDGE_GATE) {
                return BRIDGE_GATE_RAILTRACK_ASPECT;
            }
        }

        java.util.concurrent.atomic.AtomicBoolean isDisconnected =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        track.forEach(route -> {
            if (!isConnected(track, route.getFirst()) || !isConnected(track, route.getSecond())) {
                isDisconnected.set(true);
            }
        });
        if (track.getNumRoutes() == 0) {
            Dir dir = track.getFirstOpenDir();
            if (dir != null && !isConnected(track, dir)) {
                isDisconnected.set(true);
            }
        }

        if (isDisconnected.get()) {
            return DEAD_END_ASPECT;
        }

        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return CURVE_RAIL_TRACK_ASPECT;
        } else {
            return getCrossAspect(track);
        }
    }

    private boolean isConnected(Track track, Dir dir) {
        if (dir == null) {
            return false;
        }
        Track neighbor = track.getConnected(dir);
        if (neighbor == null) {
            return false;
        }
        return neighbor.getRouter().getDir(dir.inverse()) != null;
    }

    private String dirGraphicAspect(Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
            case W:
                return HORIZONTAL_DIR;
            case NE:
            case SW:
                return DIAGONAL_DIR;
            case N:
            case S:
                return VERTICAL_DIR;
            case NW:
            case SE:
                return ANTI_DIAGONAL_DIR;
        }
        return "?";
    }

    private String cursorGraphicAspect(Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
                return CURSOR_ASPECT_E;
            case W:
                return CURSOR_ASPECT_W;
            case NE:
                return CURSOR_ASPECT_NE;
            case SW:
                return CURSOR_ASPECT_SW;
            case N:
                return CURSOR_ASPECT_N;
            case S:
                return CURSOR_ASPECT_S;
            case NW:
                return CURSOR_ASPECT_NW;
            case SE:
                return CURSOR_ASPECT_SE;
        }
        return "?";
    }

    public String getCrossAspect(Track track) {
        SimpleRouter r = (SimpleRouter) (track.getRouter());
        if (r.isMixedCross()) {
            return MIXED_RAIL_CROSS_ASPECT;
        } else if (r.isHorizontalOrVertical()) {
            return RAIL_CROSS_ASPECT;
        } else {
            return DIAGONAL_RAIL_CROSS_ASPECT;
        }
    }

    public String getCrashAspect() {
        return "" + CRASH_ASPECTS[(int) (Math.random() * CRASH_ASPECTS.length)];
    }

    public TextColor getCrashColor() {
        int[] fire = crashColors();
        return palette.colorOf(fire[(int) (Math.random() * fire.length)]);
    }

    /** Variantes de fuego del choque derivadas del token, para no salirse de la familia. */
    private int[] crashColors() {
        int crash = rgb(TerminalPalette.Token.CRASH);
        return new int[] {crash, TerminalPalette.mix(crash, 0xFFD24A, 0.5f),
                TerminalPalette.mix(crash, 0xFFFFFF, 0.35f),
                TerminalPalette.mix(crash, 0x000000, 0.3f)};
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
        groundMap.forEach(ground -> visitGround(ground));
    }

    @Override
    public void visitGround(Ground ground) {
        int type = ground.getType();
        int x = ground.getPosition().getX();
        int y = ground.getPosition().getY();
        String aspect = GROUND_ASPECT;
        TextColor color = null;
        TerminalPalette.Token token = TerminalPalette.Token.GROUND;

        if (type >= 10 && type <= 19) {
            letrain.track.CargoTypes cargo =
                    letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(type);
            color = getCargoColor(cargo, true);
            aspect = PRODUCER_ASPECT;
            token = null;
        } else if (type >= 20 && type <= 29) {
            letrain.track.CargoTypes cargo =
                    letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(type);
            color = getCargoColor(cargo, true);
            aspect = CONSUMER_ASPECT;
            token = null;
        } else {
            switch (type) {
                case GroundMap.GROUND:
                    aspect = GROUND_ASPECT;
                    break;
                case GroundMap.WATER:
                    token = TerminalPalette.Token.WATER;
                    aspect = WATER_ASPECT;
                    break;
                case GroundMap.ROCK:
                    token = TerminalPalette.Token.ROCK;
                    aspect = ROCK_ASPECT;
                    break;
            }
        }
        view.setFgColor(token != null ? headlightColor(token, x, y) : color);
        applyHeadlightBackground(x, y);
        view.set(x, y, aspect);
        resetColors();
    }

    private TextColor getCargoColor(letrain.track.CargoTypes cargo, boolean isLoaded) {
        if (cargo == null) {
            return color(TerminalPalette.Token.LABEL);
        }
        TerminalPalette.Token token = switch (cargo) {
            case COAL -> TerminalPalette.Token.CARGO_COAL;
            case GOLD -> TerminalPalette.Token.CARGO_GOLD;
            case RUBY -> TerminalPalette.Token.CARGO_RUBY;
            default -> TerminalPalette.Token.LABEL;
        };
        int value = rgb(token);
        if (!isLoaded) {
            value = TerminalPalette.mix(value, rgb(TerminalPalette.Token.BOARD), 0.35f);
        }
        return palette.colorOf(value);
    }

    private TextColor getTrackBlockedColor(RailTrack track) {

        if (model == null || track == null) {
            return null;
        }
        Train ownerTrain = null;
        RailwayGraph graph = model.getRailwayGraph();
        BlockManager blockManager = model.getBlockManager();
        if (graph != null && blockManager != null) {
            Segment segment = graph.getSegment(track);
            if (segment != null) {
                List<Train> owners = blockManager.getOwners(segment);
                if (owners != null && !owners.isEmpty()) {
                    ownerTrain = owners.get(0);
                }
            }
        }
        if (ownerTrain == null && track.getLinker() != null) {
            ownerTrain = track.getLinker().getTrain();
        }
        if (ownerTrain == null) {
            return null;
        }
        Locomotive loco = null;
        if (ownerTrain.getDirectorLinker() instanceof Locomotive) {
            loco = (Locomotive) ownerTrain.getDirectorLinker();
        } else {
            for (Linker l : ownerTrain.getLinkers()) {
                if (l instanceof Locomotive) {
                    loco = (Locomotive) l;
                    break;
                }
            }
        }
        if (loco != null && loco.getColor() != null) {
            return parseColor(loco.getColor());
        }
        return null;
    }

    private TextColor parseColor(String colorName) {
        if (colorName == null || colorName.isBlank()) {
            return null;
        }
        String upper = colorName.toUpperCase();
        if (upper.equals("GRAY") || upper.equals("GREY")) {
            return TextColor.ANSI.BLACK_BRIGHT;
        } else if (upper.equals("ORANGE")) {
            return new TextColor.RGB(255, 165, 0); // Lanterna will downsample this automatically if
                                                   // needed
        } else if (upper.equals("PINK")) {
            return new TextColor.RGB(255, 192, 203);
        }
        try {
            return TextColor.ANSI.valueOf(upper);
        } catch (Exception e) {
            try {
                return TextColor.Factory.fromString(colorName);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
        // Not rendered in terminal mode
    }
}
