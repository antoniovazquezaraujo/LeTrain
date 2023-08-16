package letrain.visitor;

import java.util.Random;

import com.googlecode.lanterna.TextColor;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.View;
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
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderVisitor implements Visitor {
    Logger log = LoggerFactory.getLogger(RenderVisitor.class);
    private static final TextColor GROUND_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor WATER_COLOR = TextColor.ANSI.BLUE_BRIGHT;
    private static final TextColor ROCK_COLOR = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor CURSOR_DRAWING_COLOR = TextColor.ANSI.GREEN_BRIGHT;
    private static final TextColor CURSOR_MOVING_COLOR = TextColor.ANSI.YELLOW_BRIGHT;
    private static final TextColor CURSOR_ERASING_COLOR = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor WAGON_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor LOCOMOTIVE_COLOR = TextColor.ANSI.WHITE;
    private static final TextColor SELECTED_LOCOMOTIVE_COLOR = TextColor.ANSI.RED_BRIGHT;
    private static final TextColor RAIL_TRACK_COLOR = TextColor.ANSI.BLACK_BRIGHT;
    private static final TextColor SENSOR_COLOR = TextColor.ANSI.CYAN_BRIGHT;
    private static final TextColor STATION_COLOR = TextColor.ANSI.MAGENTA_BRIGHT;
    private static final TextColor SELECTED_STATION_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor FORK_COLOR = TextColor.ANSI.WHITE_BRIGHT;
    public static final TextColor SELECTED_FORK_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor FG_COLOR = TextColor.ANSI.WHITE;
    public static final TextColor BG_COLOR = TextColor.ANSI.BLACK;
    public static final TextColor SELECTED_LINKER_COLOR = TextColor.ANSI.YELLOW;
    public static final TextColor SEMAPHORE_OPEN_COLOR = TextColor.ANSI.GREEN;
    public static final TextColor SEMAPHORE_CLOSED_COLOR = TextColor.ANSI.RED;
    public static final TextColor SEMAPHORE_COLOR = TextColor.ANSI.BLUE;
    public static final TextColor SELECTED_SEMAPHORE_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor[] CRASH_COLORS = {
            TextColor.ANSI.RED,
            TextColor.ANSI.RED_BRIGHT,
            TextColor.ANSI.YELLOW,
            TextColor.ANSI.YELLOW_BRIGHT,
            TextColor.ANSI.BLACK
    };

    public static final char[] CRASH_ASPECTS = {
            '⁖',
            '⁘',
            '⁙',
            '⁚',
            '⁛',
            '⁝',
            '⁞',
            '․',
            '‥',
            '…',
            '⋯',
            '⋰',
            '⋱'
    };

    public static String TUNNEL_RAILTRACK_ASPECT = ".";
    public static String GROUND_ASPECT = " ";
    public static String WATER_ASPECT = "~";
    public static String ROCK_ASPECT = "*";
    public static String TUNNEL_GATE_RAILTRACK_ASPECT = "⋂";
    public static String BRIDGE_RAILTRACK_ASPECT = "\u252C";
    public static String BRIDGE_GATE_RAILTRACK_ASPECT = "\u224E";
    public static String SENSOR_ASPECT = "₪";
    public static String STATION_ASPECT = "\u27F0";
    public static String RAIL_CROSS_ASPECT = "+";
    public static String DIAGONAL_RAIL_CROSS_ASPECT = "X";
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
    public static String HORIZONTAL_DIR = "-";
    public static String VERTICAL_DIR = "|";
    public static String DIAGONAL_DIR = "/";
    public static String ANTI_DIAGONAL_DIR = "\\";

    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    Station selectedStation;
    RailSemaphore selectedSemaphore;
    private final View view;
    private GameMode mode;
    boolean showId = false;

    public RenderVisitor(View view) {
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
    }

    @Override
    public void visitModel(Model model) {
        this.showId = model.isShowId();
        this.mode = model.getMode();
        selectedLocomotive = model.getSelectedLocomotive();
        selectedFork = model.getSelectedFork();
        selectedStation = model.getSelectedStation();
        selectedSemaphore = model.getSelectedSemaphore();
        model.getGroundMap().accept(this);
        model.getRailMap().accept(this);
        model.getSensors().forEach(t -> t.accept(this));
        model.getForks().forEach(t -> t.accept(this));
        model.getSemaphores().forEach(t -> t.accept(this));
        model.getWagons().forEach(t -> t.accept(this));
        model.getLocomotives().forEach(t -> t.accept(this));
        model.getStations().forEach(t -> t.accept(this));
        visitCursor(model.getCursor());
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        if (track.getSensor() != null) {
            if (track.getSensor() instanceof Station) {
                view.setFgColor(STATION_COLOR);
            } else {
                view.setFgColor(SENSOR_COLOR);
            }
        } else {
            view.setFgColor(RAIL_TRACK_COLOR);
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
        resetColors();
    }

    @Override
    public void visitStation(Station station) {
        Track track = station.getTrack();
        if (this.mode == GameMode.STATIONS) {
            if (station == selectedStation) {
                view.setFgColor(SELECTED_STATION_COLOR);
            } else {
                view.setFgColor(STATION_COLOR);
            }

            view.set(track.getPosition().getX(), track.getPosition().getY(), STATION_ASPECT + station.getId());
        }
        resetColors();
    }

    @Override
    public void visitSensor(Sensor sensor) {
        Track track = sensor.getTrack();
        if (track.getSensor() != null && this.mode == GameMode.RAILS) {
            if (track.getSensor() instanceof Station) {
                view.setFgColor(STATION_COLOR);
            } else {
                view.setFgColor(SENSOR_COLOR);
            }
            view.set(track.getPosition().getX(), track.getPosition().getY(), SENSOR_ASPECT + track.getSensor().getId());
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
        view.set(pos.getX() + 1, pos.getY(), "" + (mode.equals(GameMode.SEMAPHORES) ? semaphore.getId() : ""));
        resetColors();
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        if (track == selectedFork) {
            view.setFgColor(SELECTED_FORK_COLOR);
        } else {
            view.setFgColor(FORK_COLOR);
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(), dirGraphicAspect(track.getFirstOpenDir()));
        if (this.mode == GameMode.FORKS) {
            view.set(track.getPosition().getX() + 1, track.getPosition().getY(), "" + track.getId());
        }
        resetColors();
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        if (locomotive.getTrack().getClass().equals(TunnelRailTrack.class) && this.mode != GameMode.RAILS) {
            return;
        }
        if (locomotive.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), getCrashAspect());
            resetColors();
            return;
        }
        if (locomotive == selectedLocomotive) {
            view.setFgColor(SELECTED_LOCOMOTIVE_COLOR);
        } else {
            view.setFgColor(LOCOMOTIVE_COLOR);
        }
        if (locomotive.isShowingDir()) {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(),
                    cursorGraphicAspect(locomotive.getDir()));
        } else {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), locomotive.getAspect());
            view.set(locomotive.getPosition().getX() + 1, locomotive.getPosition().getY(),
                    (isShowId() ? ("" + locomotive.getId()) : ""));
        }
        view.setBgColor(SELECTED_LINKER_COLOR);
        if (locomotive.getTrain() != null) {
            String aspect = "";
            for (Linker linkerToJoin : locomotive.getTrain().getLinkersToJoin()) {
                if (linkerToJoin != null) {
                    if (linkerToJoin instanceof Locomotive) {
                        aspect = ((Locomotive) linkerToJoin).getAspect();
                    } else {
                        aspect = ((Wagon) linkerToJoin).getAspect();
                    }
                    view.set(linkerToJoin.getPosition().getX(), linkerToJoin.getPosition().getY(), aspect);
                }
            }
            for (Linker linkerToPreserve : locomotive.getTrain().getLinkersToRemove()) {
                if (linkerToPreserve != null) {
                    if (linkerToPreserve instanceof Locomotive) {
                        aspect = ((Locomotive) linkerToPreserve).getAspect();
                    } else {
                        aspect = ((Wagon) linkerToPreserve).getAspect();
                    }
                    view.set(linkerToPreserve.getPosition().getX(), linkerToPreserve.getPosition().getY(), aspect);
                }
            }
            resetColors();
        }
    }

    @Override
    public void visitWagon(Wagon wagon) {
        if (wagon.getTrack().getClass().equals(TunnelRailTrack.class) &&
                this.mode != GameMode.RAILS) {
            return;
        }
        if (wagon.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), getCrashAspect());
            return;
        }
        if (wagon.getTrain() != null && wagon.getTrain().isLoading) {
            view.setFgColor(TextColor.ANSI.values()[new Random().nextInt(TextColor.ANSI.values().length)]);
        } else {
            view.setFgColor(WAGON_COLOR);
        }
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
        resetColors();
    }

    @Override
    public void visitCursor(Cursor cursor) {
        switch (cursor.getMode()) {
            case DRAWING:
                view.setFgColor(CURSOR_DRAWING_COLOR);
                break;
            case ERASING:
                view.setFgColor(CURSOR_ERASING_COLOR);
                break;
            case MOVING:
                view.setFgColor(CURSOR_MOVING_COLOR);
                break;
        }
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), cursorGraphicAspect(cursor.getDir()));
        resetColors();
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track instanceof StationRailTrack) {
            return STATION_RAIL_TRACK_ASPECT;
        }
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return CURVE_RAIL_TRACK_ASPECT;
        } else {
            return getCrossAspect(track);
        }
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
        if (r.isHorizontalOrVertical()) {
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
        String aspect = " ";
        switch (type) {
            case GroundMap.GROUND:
                view.setFgColor(GROUND_COLOR);
                aspect = GROUND_ASPECT;
                break;
            case GroundMap.WATER:
                view.setFgColor(WATER_COLOR);
                aspect = WATER_ASPECT;
                break;
            case GroundMap.ROCK:
                view.setFgColor(ROCK_COLOR);
                aspect = ROCK_ASPECT;
                break;
        }
        view.set(x, y, aspect);
        resetColors();
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), BRIDGE_GATE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), BRIDGE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), TUNNEL_GATE_RAILTRACK_ASPECT);
        resetColors();
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        if (this.mode == GameMode.RAILS) {
            view.setFgColor(RAIL_TRACK_COLOR);
            view.set(track.getPosition().getX(), track.getPosition().getY(), TUNNEL_RAILTRACK_ASPECT);
            resetColors();
        }
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitEconomyManager'");
    }

}
