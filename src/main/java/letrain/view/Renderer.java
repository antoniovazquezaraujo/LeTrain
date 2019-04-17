package letrain.view;

import letrain.map.RailMap;
import letrain.map.RailTrackMaker;
import letrain.sim.GameModel;
import letrain.track.StopTrack;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Renderer {
     void renderSim(GameModel model);

     void renderMap(RailMap map);

     void renderTrack(Track track);

     void renderStopTrack(StopTrack track );

    //TODO
    //     void renderForkTrack(ForkTrack);

     void renderTrain(Train train);

     void renderLinker(Linker linker);

     void renderLocomotive(Locomotive locomotive);

     void renderWagon(Wagon wagon);

     void renderRailTrackMaker(RailTrackMaker railTrackMaker);
}
