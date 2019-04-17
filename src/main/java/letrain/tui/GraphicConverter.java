package letrain.tui;

import letrain.map.RailTrackMaker;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;

public interface GraphicConverter {
    String getLinkerAspect(Linker linker);
    String getTrackAspect(Track track);
    String getRailTrackMakerAspect(RailTrackMaker maker);
}
