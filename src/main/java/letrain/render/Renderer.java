package letrain.render;

import letrain.map.RailMap;
import letrain.mvp.GameModel;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Renderer {
    void renderModel(GameModel model);

    void renderTrain(Train train);

    void renderMap(RailMap map);

    void renderRailTrack(RailTrack track);

    void renderStopRailTrack(StopRailTrack track);

    void renderForkRailTrack(ForkRailTrack track);

    void renderTrainFactoryRailTrack(TrainFactoryRailTrack track);

    void renderTunnelRailTrack(TunnelRailTrack track);

    void renderLinker(Linker linker);

    void renderLocomotive(Locomotive locomotive);

    void renderWagon(Wagon wagon);

    void renderCursor(Cursor cursor);
}
