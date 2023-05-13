package letrain.visitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TextColor;

import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.map.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.Model.GameMode;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
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
    public static final TextColor FORK_COLOR = TextColor.ANSI.WHITE_BRIGHT;
    public static final TextColor SELECTED_FORK_COLOR = TextColor.ANSI.RED_BRIGHT;
    public static final TextColor FG_COLOR = TextColor.ANSI.WHITE;
    public static final TextColor BG_COLOR = TextColor.ANSI.BLACK;
    public static final TextColor SELECTED_LINKER_COLOR = TextColor.ANSI.YELLOW;
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    private final View view;
    private GameMode mode;

    public RenderVisitor(View view) {
        this.view = view;
        view.setFgColor(FG_COLOR);
        view.setBgColor(BG_COLOR);
    }

    @Override
    public void visitModel(Model model) {
        this.mode = model.getMode();
        selectedLocomotive = model.getSelectedLocomotive();
        selectedFork = model.getSelectedFork();
        model.getRailMap().accept(this);
        model.getForks().forEach(t -> t.accept(this));
        model.getWagons().forEach(t -> t.accept(this));
        model.getLocomotives().forEach(t -> t.accept(this));
        // if (model.getMode() == GameMode.RAILS) {
        visitCursor(model.getCursor());
        // }
    }

    @Override
    public void visitMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
    }

    @Override
    public void visitStopRailTrack(StopRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⍚");
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

    }

    @Override
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        view.setFgColor(TextColor.ANSI.BLUE_BRIGHT);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⎵");
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        view.setFgColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⋂");
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
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
            view.setBgColor(BG_COLOR);
            view.setFgColor(FG_COLOR);
        }

    }

    @Override
    public void visitLinker(Linker linker) {
        view.setFgColor(TextColor.ANSI.YELLOW_BRIGHT);
        view.set(linker.getPosition().getX(), linker.getPosition().getY(), "?");
    }

    @Override
    public void visitWagon(Wagon wagon) {
        view.setFgColor(TextColor.ANSI.WHITE);
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
    }

    @Override
    public void visitCursor(Cursor cursor) {
        TextColor oldColor = view.getFgColor();
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
        view.setFgColor(oldColor);
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
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
                return "" + Symbols.SINGLE_LINE_TOP_RIGHT_CORNER;
            case SW:
                return "" + Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER;
            case N:
                return "^";
            case S:
                return "v";
            case NW:
                return "" + Symbols.SINGLE_LINE_TOP_LEFT_CORNER;
            case SE:
                return "" + Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER;
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
}
