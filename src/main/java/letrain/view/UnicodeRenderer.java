package letrain.view;

import javafx.scene.paint.Color;
import letrain.gui.LeTrainView;
import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.trackmaker.RailTrackMaker;
import letrain.sim.GameModel;
import letrain.track.StopTrack;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class UnicodeRenderer implements Renderer {
    private final LeTrainView view;
    public UnicodeRenderer(LeTrainView view) {
        this.view = view;
    }

    @Override
    public void renderSim(GameModel model) {
        model.getMap().accept(this);
        model.getTrains().forEach(t->t.accept(this));
        model.getMaker().accept(this);
    }

    @Override
    public void renderMap(RailMap map) {
        map.forEach(t-> t.accept(this));
    }

    @Override
    public void renderTrack(Track track) {
        if (track.getRouter().isStraight()) {
            view.setColor(Color.YELLOW);
            view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
        } else if (track.getRouter().isCurve()) {
            view.setColor(Color.YELLOW);
            view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
        }else if (track.getRouter().isFork()){
            view.setColor(Color.GREEN);
            view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));
        } else {
            view.setColor(Color.YELLOW);
            view.set(track.getPosition().getX(), track.getPosition().getY(), "+");
        }

    }

    @Override
    public void renderStopTrack(StopTrack stopTrack) {
        view.setColor(Color.YELLOW);
        view.set(stopTrack.getPosition().getX(), stopTrack.getPosition().getY(), "☰");
    }

    @Override
    public void renderTrain(Train train) {
        train.getLinkers().forEach(t->t.accept(this));
    }

    @Override
    public void renderLinker(Linker linker) {
        view.set(linker.getPosition().getX(), linker.getPosition().getY(), "?");
    }

    @Override
    public void renderLocomotive(Locomotive locomotive) {
        view.set(locomotive.getPosition().getX(), locomotive.getPosition().getY(), "L");
    }

    @Override
    public void renderWagon(Wagon wagon) {
        view.set(wagon.getPosition().getX(), wagon.getPosition().getY(), "W");
    }

    @Override
    public void renderRailTrackMaker(RailTrackMaker railTrackMaker) {
        view.setColor(Color.RED);
        view.set(railTrackMaker.getPosition().getX(), railTrackMaker.getPosition().getY(), dirGraphicAspect(railTrackMaker.getDirection()));
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return "∙";
        }else if (track.getRouter().isFork()){
                return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else {
            return "+";
        }
    }
    private String dirGraphicAspect(Dir dir){
        if(dir==null){
            return "";
        }
        switch(dir) {
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

}
