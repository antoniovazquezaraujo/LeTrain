package letrain.visitor.terminal;

import com.googlecode.lanterna.TextColor;
import java.util.List;
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
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StationRailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
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
    private static final TextColor GROUND_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor WATER_COLOR = TextColor.ANSI.BLUE_BRIGHT;
    private static final TextColor ROCK_COLOR = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor CURSOR_DRAWING_COLOR = TextColor.ANSI.GREEN_BRIGHT;
    private static final TextColor CURSOR_MOVING_COLOR = TextColor.ANSI.YELLOW_BRIGHT;
    private static final TextColor CURSOR_ERASING_COLOR = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor WAGON_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor LOCOMOTIVE_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor RAIL_TRACK_COLOR = TextColor.ANSI.BLACK_BRIGHT;
    private static final TextColor SENSOR_COLOR = TextColor.ANSI.CYAN_BRIGHT;
    private static final TextColor STATION_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor SELECTED_STATION_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor FORK_COLOR = TextColor.ANSI.WHITE_BRIGHT;
    public static final TextColor SELECTED_FORK_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor FG_COLOR = TextColor.ANSI.WHITE;
    public static final TextColor BG_COLOR = TextColor.ANSI.BLACK;
    public static final TextColor SELECTED_LINKER_COLOR = TextColor.ANSI.MAGENTA;
    public static final TextColor SEMAPHORE_OPEN_COLOR = TextColor.ANSI.GREEN;
    public static final TextColor SEMAPHORE_CLOSED_COLOR = TextColor.ANSI.RED;
    public static final TextColor SEMAPHORE_COLOR = TextColor.ANSI.BLUE;
    public static final TextColor SELECTED_SEMAPHORE_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor[] CRASH_COLORS = {TextColor.ANSI.RED, TextColor.ANSI.RED_BRIGHT,
            TextColor.ANSI.YELLOW, TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLACK};

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
    public static String STATION_RAIL_TRACK_ASPECT = "#";
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
        this.view = view;
        view.setFgColor(FG_COLOR);
        view.setBgColor(BG_COLOR);
    }

    boolean isShowId() {
        return showId;
    }

    public void resetColors() {
        view.setFgColor(FG_COLOR);
        view.setBgColor(BG_COLOR);
        view.setUnderline(false);
    }

    @Override
    public void visitModel(Model model) {
        this.model = model;
        if (model == null) {
            return;
        }
        this.showId = model.isShowId();
        this.mode = model.getMode();
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
        TextColor blockedColor = getTrackBlockedColor(track);
        String aspect = getTrackAspect(track);

        if (blockedColor != null) {
            view.setFgColor(blockedColor);
        } else if (track.getComponent() instanceof letrain.track.Sensor) {
            if (track.getComponent() instanceof Station) {
                view.setFgColor(STATION_COLOR);
            } else {
                view.setFgColor(SENSOR_COLOR);
            }
        } else if (DEAD_END_ASPECT.equals(aspect)) {
            view.setFgColor(TextColor.ANSI.YELLOW);
        } else {
            view.setFgColor(RAIL_TRACK_COLOR);
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(), aspect);
        resetColors();
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
        if (station == selectedStation && this.mode == GameMode.STATIONS) {
            view.setFgColor(SELECTED_STATION_COLOR);
        } else if (station.getCargoType() != letrain.track.CargoTypes.NONE) {
            boolean isProducer = station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER;
            view.setFgColor(getCargoColor(station.getCargoType(), isProducer));
        } else {
            view.setFgColor(STATION_COLOR);
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
                // To blink, we simply don't draw the station, leaving the track visible underneath.
                resetColors();
                return;
            }
        }

        // Remove underline logic for station since we are doing appear/disappear blink
        view.setUnderline(false);

        view.set(track.getPosition().getX(), track.getPosition().getY(),
                aspect + (this.mode == GameMode.STATIONS ? station.getId() : ""));
        resetColors();
    }

    @Override
    public void visitSensor(Sensor sensor) {
        Track track = sensor.getTrack();
        if (track.getComponent() instanceof letrain.track.Sensor) {
            if (track.getComponent() instanceof Station) {
                // Stations are handled by visitStation, but if visited here we could skip or just not interfere.
                // It's probably better to just return if it's a Station so visitStation handles it completely.
                if (!(track.getComponent() instanceof Station)) {
                    view.setFgColor(SENSOR_COLOR);
                    view.set(track.getPosition().getX(), track.getPosition().getY(),
                            SENSOR_ASPECT + (this.mode == GameMode.SENSORS ? sensor.getId() : ""));
                }
            } else {
                view.setFgColor(SENSOR_COLOR);
                view.set(track.getPosition().getX(), track.getPosition().getY(),
                        SENSOR_ASPECT + (this.mode == GameMode.SENSORS ? sensor.getId() : ""));
            }
        }
        resetColors();
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        Point pos = semaphore.getPosition();
        if (semaphore.isOpen()) {
            view.setFgColor(SEMAPHORE_OPEN_COLOR);
        } else {
            view.setFgColor(SEMAPHORE_CLOSED_COLOR);
        }
        view.set(pos.getX(), pos.getY(), SEMAPHORE_ASPECT);
        if (semaphore == selectedSemaphore) {
            view.setFgColor(SELECTED_SEMAPHORE_COLOR);
        } else {
            view.setFgColor(SEMAPHORE_COLOR);
        }
        view.set(pos.getX() + 1, pos.getY(),
                "" + (mode.equals(GameMode.SEMAPHORES) ? semaphore.getId() : ""));
        resetColors();
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {
        letrain.map.Point pos = speedSignal.getPosition();
        if (speedSignal == selectedSpeedSignal && mode == GameMode.SPEED_SIGNALS) {
            view.setUnderline(true);
        }
        if (speedSignal.isMax()) {
            view.setFgColor(TextColor.ANSI.RED);
        } else {
            view.setFgColor(TextColor.ANSI.BLUE);
        }

        int limit = speedSignal.getLimit();
        char icon;
        if (limit >= 1 && limit <= 10) {
            icon = (char) ('\u245F' + limit);
        } else {
            icon = '?';
        }

        String arrow = speedSignalArrow(speedSignal.getCreationDir());

        view.set(pos.getX(), pos.getY(), String.valueOf(icon));
        view.set(pos.getX() + 1, pos.getY(), arrow);
        view.set(pos.getX() + 2, pos.getY(), mode == GameMode.SPEED_SIGNALS ? String.valueOf(speedSignal.getId()) : "");

        view.setUnderline(false);
        view.setFgColor(TextColor.ANSI.WHITE);
        resetColors();
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
        if (track == selectedFork) {
            view.setFgColor(SELECTED_FORK_COLOR);
        } else {
            TextColor blockedColor = getTrackBlockedColor(track);
            if (blockedColor != null) {
                view.setFgColor(blockedColor);
            } else {
                view.setFgColor(FORK_COLOR);
            }
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(),
                dirGraphicAspect(track.getFirstOpenDir()));
        if (this.mode == GameMode.FORKS) {
            view.set(track.getPosition().getX() + 1, track.getPosition().getY(),
                    "" + track.getId());
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
                view.setBgColor(SELECTED_LINKER_COLOR);
                view.setFgColor(TextColor.ANSI.BLACK);
            }
        }
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        if ((locomotive.getTrack().getClass().equals(TunnelRailTrack.class)
                || locomotive.getTrack().getClass().equals(TunnelGateRailTrack.class))
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
        view.setFgColor(locoColor != null ? locoColor : LOCOMOTIVE_COLOR);
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
        if ((wagon.getTrack().getClass().equals(TunnelRailTrack.class)
                || wagon.getTrack().getClass().equals(TunnelGateRailTrack.class))
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
            view.setFgColor(WAGON_COLOR);
        }
        highlightIfSelected(wagon);
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
        resetColors();
    }

    @Override
    public void visitCursor(Cursor cursor) {
        String aspect = cursorGraphicAspect(cursor.getDir());
        switch (cursor.getMode()) {
            case DRAWING:
                view.setFgColor(CURSOR_DRAWING_COLOR);
                break;
            case MAKING_TRACKS:
                // Draw braille on the tile being constructed (previous position)
                letrain.map.Point cp = cursor.getConstructionPosition();
                if (cp != null) {
                    String[] braille = {"⡀", "⢀", "⡄", "⣤", "⣦", "⣶", "⣾", "⣿"};
                    int idx2 = (int) (cursor.getProgress() * (braille.length - 1));
                    String brailleChar = braille[Math.max(0, Math.min(idx2, braille.length - 1))];
                    view.setFgColor(TextColor.ANSI.YELLOW);
                    view.set(cp.getX(), cp.getY(), brailleChar);
                    resetColors();
                }
                // Draw cursor normally on its own position
                view.setFgColor(CURSOR_DRAWING_COLOR);
                break;
            case ERASING:
                view.setFgColor(CURSOR_ERASING_COLOR);
                break;
            case MOVING:
                view.setFgColor(CURSOR_MOVING_COLOR);
                break;
        }
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), aspect);
        resetColors();
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track instanceof StationRailTrack) {
            return STATION_RAIL_TRACK_ASPECT;
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
        return CRASH_COLORS[(int) (Math.random() * CRASH_COLORS.length)];
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
        TextColor color = GROUND_COLOR;

        if (type >= 10 && type <= 19) {
            letrain.track.CargoTypes cargo =
                    letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(type);
            color = getCargoColor(cargo, true);
            aspect = PRODUCER_ASPECT;
        } else if (type >= 20 && type <= 29) {
            letrain.track.CargoTypes cargo =
                    letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(type);
            color = getCargoColor(cargo, true);
            aspect = CONSUMER_ASPECT;
        } else {
            switch (type) {
                case GroundMap.GROUND:
                    color = GROUND_COLOR;
                    aspect = GROUND_ASPECT;
                    break;
                case GroundMap.WATER:
                    color = WATER_COLOR;
                    aspect = WATER_ASPECT;
                    break;
                case GroundMap.ROCK:
                    color = ROCK_COLOR;
                    aspect = ROCK_ASPECT;
                    break;
            }
        }
        view.setFgColor(color);
        view.set(x, y, aspect);
        resetColors();
    }

    private TextColor getCargoColor(letrain.track.CargoTypes cargo, boolean isLoaded) {
        if (cargo == null) {
            return TextColor.ANSI.WHITE;
        }
        switch (cargo) {
            case COAL:
                return isLoaded ? TextColor.ANSI.WHITE : TextColor.ANSI.BLACK_BRIGHT;
            case GOLD:
                return isLoaded ? TextColor.ANSI.YELLOW_BRIGHT : TextColor.ANSI.YELLOW;
            case RUBY:
                return isLoaded ? TextColor.ANSI.RED_BRIGHT : TextColor.ANSI.RED;
            case NONE:
            default:
                return TextColor.ANSI.WHITE;
        }
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack track) {
        TextColor blockedColor = getTrackBlockedColor(track);
        view.setFgColor(blockedColor != null ? blockedColor : RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(),
                BRIDGE_GATE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack track) {
        TextColor blockedColor = getTrackBlockedColor(track);
        view.setFgColor(blockedColor != null ? blockedColor : RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), BRIDGE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack track) {
        TextColor blockedColor = getTrackBlockedColor(track);
        view.setFgColor(blockedColor != null ? blockedColor : RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(),
                TUNNEL_GATE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        if (this.mode == GameMode.RAILS) {
            TextColor blockedColor = getTrackBlockedColor(track);
            view.setFgColor(blockedColor != null ? blockedColor : RAIL_TRACK_COLOR);
            view.set(track.getPosition().getX(), track.getPosition().getY(),
                    TUNNEL_RAILTRACK_ASPECT);
            resetColors();
        }
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
