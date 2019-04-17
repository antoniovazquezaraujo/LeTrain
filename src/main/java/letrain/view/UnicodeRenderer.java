package letrain.view;

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
    LeTrainView view;
    public UnicodeRenderer(LeTrainView view) {
        this.view = view;
    }
//    @Override
//    public void paint(Renderer renderer) {
//        renderer.renderSim(model);
//        model.getMap().forEach(t -> {
//            view.set(
//                    t.getPosition().getX(),
//                    t.getPosition().getY(),
//                    converter.getTrackAspect(t));
//        });
//        model.getTrains().forEach(train -> {
//            train.getLinkers().forEach(linker -> {
//                view.set(
//                        linker.getPosition().getX(),
//                        linker.getPosition().getY(), converter.getLinkerAspect(linker));
//            });
//        });
//        view.set(model.getMaker().getPosition().getX(), model.getMaker().getPosition().getY(), converter.getRailTrackMakerAspect(model.getMaker()));
//
//    }

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
        view.set(track.getPosition().getX(), track.getPosition().getY(), getTrackAspect(track));

    }

    @Override
    public void renderStopTrack(StopTrack stopTrack) {
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
        view.set(railTrackMaker.getPosition().getX(), railTrackMaker.getPosition().getY(), "X");
    }

    ////////////////////////////////////////////////////////////////////////////////
    private String getTrackAspect(Track track) {
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return ".";
        }else if (track.getRouter().isFork()){
            return "Y";
//            if(track.getRouter().isUsingAlternativeRoute()){
//                return dirGraphicAspect(track.getRouter().getAlternativeRoute().getTarget());
//            }else{
//                return dirGraphicAspect(track.getRouter().getOriginalRoute().getTarget());
//            }
        } else {
            return "+";
        }
    }
    private String dirGraphicAspect(Dir dir){
        if(dir==null){
            return "?";
        }
        switch(dir) {
            case E:
            case W:
                return "-";
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
