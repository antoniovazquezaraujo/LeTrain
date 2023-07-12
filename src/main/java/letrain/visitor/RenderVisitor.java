package letrain.visitor;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TextColor;

import letrain.ground.Ground;
import letrain.ground.Ground.GroundType;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.View;
import letrain.track.Platform;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.PlatformTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public class RenderVisitor implements Visitor {
    Logger log = LoggerFactory.getLogger(RenderVisitor.class);
    private static final TextColor RAIL_TRACK_COLOR = TextColor.ANSI.BLACK_BRIGHT;
    private static final TextColor SENSOR_COLOR = TextColor.ANSI.CYAN_BRIGHT;
    private static final TextColor PLATFORM_COLOR = TextColor.ANSI.MAGENTA_BRIGHT;
    private static final TextColor SELECTED_PLATFORM_COLOR = TextColor.ANSI.RED_BRIGHT;
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
            '*', '+', 'X', 'x', '*', '.', '-', '@', '#', '0'
    };

    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    Platform selectedPlatform;
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
        selectedPlatform = model.getSelectedPlatform();
        selectedSemaphore = model.getSelectedSemaphore();
        model.getGroundMap().accept(this);
        model.getRailMap().accept(this);
        model.getSensors().forEach(t -> t.accept(this));
        model.getPlatforms().forEach(t -> t.accept(this));
        model.getForks().forEach(t -> t.accept(this));
        model.getSemaphores().forEach(t -> t.accept(this));
        model.getWagons().forEach(t -> t.accept(this));
        model.getLocomotives().forEach(t -> t.accept(this));
        visitCursor(model.getCursor());
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        if (track.getSensor() != null) {
            if (track.getSensor() instanceof Platform) {
                view.setFgColor(PLATFORM_COLOR);
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
    public void visitPlatform(Platform platform) {
        Track track = platform.getTrack();
        if (this.mode == GameMode.PLATFORMS) {
            if (platform == selectedPlatform) {
                view.setFgColor(SELECTED_PLATFORM_COLOR);
            } else {
                view.setFgColor(PLATFORM_COLOR);
            }

            view.set(track.getPosition().getX(), track.getPosition().getY(), "₪" + platform.getId());
        }
        resetColors();
    }

    @Override
    public void visitSensor(Sensor sensor) {
        Track track = sensor.getTrack();
        if (track.getSensor() != null && this.mode == GameMode.RAILS) {
            if (track.getSensor() instanceof Platform) {
                view.setFgColor(PLATFORM_COLOR);
            } else {
                view.setFgColor(SENSOR_COLOR);
            }
            view.set(track.getPosition().getX(), track.getPosition().getY(), "₪" + track.getSensor().getId());
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
        view.set(pos.getX(), pos.getY(), ":");
        if (semaphore == selectedSemaphore) {
            view.setFgColor(SELECTED_SEMAPHORE_COLOR);
        } else {
            view.setFgColor(SEMAPHORE_COLOR);
        }
        view.set(pos.getX() + 1, pos.getY(), "" + (mode.equals(GameMode.SEMAPHORES) ? semaphore.getId() : ""));
        resetColors();
    }

    @Override
    public void visitStopRailTrack(StopRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⍚");
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
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        view.setFgColor(TextColor.ANSI.BLUE_BRIGHT);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⎵");
        resetColors();
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⋂");
        resetColors();
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        if (locomotive.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), getCrashAspect());
            resetColors();
            return;
        }
        if (locomotive == selectedLocomotive) {
            view.setFgColor(TextColor.ANSI.RED_BRIGHT);
        } else {
            view.setFgColor(TextColor.ANSI.WHITE);
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
    public void visitLinker(Linker linker) {
        view.setFgColor(TextColor.ANSI.YELLOW_BRIGHT);
        view.set(linker.getPosition().getX(), linker.getPosition().getY(), "?");
        resetColors();
    }

    @Override
    public void visitWagon(Wagon wagon) {
        if (wagon.isDestroying()) {
            view.setFgColor(getCrashColor());
            // view.setBgColor(getCrashColor());
            view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), getCrashAspect());
            return;
        }
        if (wagon.getTrain() != null && wagon.getTrain().isLoading) {
            view.setFgColor(TextColor.ANSI.values()[new Random().nextInt(TextColor.ANSI.values().length)]);
        } else {
            view.setFgColor(TextColor.ANSI.WHITE);
        }
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
        resetColors();
    }

    @Override
    public void visitCursor(Cursor cursor) {
        switch (cursor.getMode()) {
            case DRAWING:
                view.setFgColor(TextColor.ANSI.GREEN_BRIGHT);
                break;
            case ERASING:
                view.setFgColor(TextColor.ANSI.RED_BRIGHT);
                break;
            case MOVING:
                view.setFgColor(TextColor.ANSI.YELLOW);
                break;
        }
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), cursorGraphicAspect(cursor.getDir()));
        resetColors();
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track instanceof PlatformTrack) {
            return "#";
        }
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return "·";
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
                return "─";
            case NE:
            case SW:
                return "/";
            case N:
            case S:
                return "|";
            case NW:
            case SE:
                return "\\";
        }
        return "?";
    }

    private String cursorGraphicAspect(Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
                return ">";
            case W:
                return "<";
            case NE:
                return "⌝";
            case SW:
                return "⌞";
            case N:
                return "⌃";
            case S:
                return "⌄";
            case NW:
                return "⌜";
            case SE:
                return "⌟";
        }
        return "?";
    }

    public String getCrossAspect(Track track) {
        SimpleRouter r = (SimpleRouter) (track.getRouter());
        if (r.isHorizontalOrVertical()) {
            return "+";
        } else {
            return "x";
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
        Ground.GroundType type = ground.getType();
        view.set(ground.getPosition().getX(), ground.getPosition().getY(), type.equals(GroundType.GROUND) ? " " : "#");
        resetColors();
    }

}
