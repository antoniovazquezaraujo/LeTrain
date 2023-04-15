package letrain.visitor;

import javafx.scene.paint.Color;
import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.map.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.View;
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
    private static final Color RAIL_TRACK_COLOR = Color.grayRgb(80);
    public static final Color FORK_COLOR = Color.grayRgb(180);
    public static final Color SELECTED_FORK_COLOR = Color.RED;
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    private final View view;

    public RenderVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(Model model) {
        selectedLocomotive = model.getSelectedLocomotive();
        selectedFork = model.getSelectedFork();
        model.getRailMap().accept(this);
        model.getLocomotives().forEach(t -> t.accept(this));
        model.getWagons().forEach(t -> t.accept(this));
        model.getForks().forEach(t -> t.accept(this));
        visitCursor(model.getCursor());
    }

    @Override
    public void visitMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        view.setColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
    }

    @Override
    public void visitStopRailTrack(StopRailTrack track) {
        view.setColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⍚");
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        if (track == selectedFork) {
            view.setColor(SELECTED_FORK_COLOR);
        } else {
            view.setColor(FORK_COLOR);
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(), dirGraphicAspect(track.getFirstOpenDir()));
    }

    @Override
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        view.setColor(Color.LIGHTBLUE);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⎵");
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        view.setColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⋂");
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        if (locomotive == selectedLocomotive) {
            view.setColor(Color.RED);
        } else {
            view.setColor(Color.LIGHTYELLOW);
        }
        if (locomotive.isShowingDir()) {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(),
                    cursorGraphicAspect(locomotive.getDir()));
        } else {
            view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), locomotive.getAspect());
        }
        view.setColor(Color.BLUEVIOLET);
        if (locomotive.getTrain() != null) {
            for (Linker linkerToJoin : locomotive.getTrain().getLinkersToJoin()) {
                view.set(linkerToJoin.getPosition().getX(), linkerToJoin.getPosition().getY(), "░");
            }
            view.setColor(Color.WHITE);
            for (Linker linkerToPreserve : locomotive.getTrain().getLinkersToRemove()) {
                view.set(linkerToPreserve.getPosition().getX(), linkerToPreserve.getPosition().getY(), "░");
            }
        }

    }

    @Override
    public void visitLinker(Linker linker) {
        view.setColor(Color.AZURE);
        view.set(linker.getPosition().getX(), linker.getPosition().getY(), "?");
    }

    @Override
    public void visitWagon(Wagon wagon) {
        view.setColor(Color.BROWN);
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
    }

    @Override
    public void visitCursor(Cursor cursor) {
        switch (cursor.getMode()) {
            case DRAWING:
                view.setColor(Color.LIGHTGREEN);
                break;
            case ERASING:
                view.setColor(Color.ORANGERED);
                break;
            case MOVING:
                view.setColor(Color.YELLOW);
                break;
        }
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), cursorGraphicAspect(cursor.getDir()));
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return "∙";
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
                return "−";
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
}
