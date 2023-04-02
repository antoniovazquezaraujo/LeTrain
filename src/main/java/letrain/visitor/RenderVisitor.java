package letrain.visitor;


import com.googlecode.lanterna.TextColor;

import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.map.SimpleRouter;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class RenderVisitor implements Visitor {
    private static final TextColor RAIL_TRACK_COLOR = new TextColor.RGB(80,80,80);
    public static final TextColor FORK_COLOR = new TextColor.RGB(80,80,80);
    public static final TextColor SELECTED_FORK_COLOR = new TextColor.RGB(255,0,0);
    Train selectedTrain;
    ForkRailTrack selectedFork;
    private final View view;

    public RenderVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(Model model) {
        selectedTrain = model.getSelectedTrain();
        selectedFork = model.getSelectedFork();
        model.getRailMap().accept(this);
        model.getTrains().forEach(t -> t.accept(this));
        model.getForks().forEach(t-> t.accept(this));
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
        if(track == selectedFork){
            view.setColor(SELECTED_FORK_COLOR);
        }else{
            view.setColor(FORK_COLOR);
        }
        view.set(track.getPosition().getX(), track.getPosition().getY(), dirGraphicAspect(track.getFirstOpenDir()));
    }

    @Override
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        view.setColor(TextColor.ANSI.BLUE_BRIGHT);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⎵");
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        view.setColor(RAIL_TRACK_COLOR);
        view.set(track.getPosition().getX(), track.getPosition().getY(), "⋂");
    }


    @Override
    public void visitTrain(Train train) {
        if(train == selectedTrain){
            view.setColor(TextColor.ANSI.RED);
        }else{
            view.setColor(TextColor.ANSI.YELLOW_BRIGHT);
        }
        train.getLinkers().forEach(t -> t.accept(this));
    }

    @Override
    public void visitLinker(Linker linker) {
        view.set(linker.getPosition().getX(), linker.getPosition().getY(), "?");
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), locomotive.getAspect());
    }

    @Override
    public void visitWagon(Wagon wagon) {
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), wagon.getAspect());
    }

    @Override
    public void visitCursor(Cursor cursor) {
        switch(cursor.getMode()){
            case DRAWING:
                view.setColor(TextColor.ANSI.GREEN_BRIGHT);
                break;
            case ERASING:
                view.setColor(TextColor.ANSI.RED_BRIGHT);
                break;
            case MOVING:
                view.setColor(TextColor.ANSI.YELLOW);
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
