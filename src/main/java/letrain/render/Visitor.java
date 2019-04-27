package letrain.render;

import letrain.map.RailMap;
import letrain.mvp.GameModel;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Visitor {
    void visitModel(GameModel model);

    void visitTrain(Train train);

    void visitMap(RailMap map);

    void visitRailTrack(RailTrack track);

    void visitStopRailTrack(StopRailTrack track);

    void visitForkRailTrack(ForkRailTrack track);

    void visitTrainFactoryRailTrack(TrainFactoryRailTrack track);

    void visitTunnelRailTrack(TunnelRailTrack track);

    void visitLinker(Linker linker);

    void visitLocomotive(Locomotive locomotive);

    void visitWagon(Wagon wagon);

    void visitCursor(Cursor cursor);
}
