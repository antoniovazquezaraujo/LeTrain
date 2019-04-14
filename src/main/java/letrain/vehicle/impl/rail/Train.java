package letrain.vehicle.impl.rail;

import letrain.track.rail.RailTrack;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class Train implements Trailer<RailTrack> {

    Deque<Linker<RailTrack>> linkers;
    Tractor<RailTrack> mainTractor;

    public Train() {
        this.linkers = new LinkedList<>();
    }


    @Override
    public Deque<Linker<RailTrack>> getLinkers() {
        return linkers;
    }

    @Override
    public Tractor<RailTrack> getMainTractor() {
        return mainTractor;
    }

    @Override
    public void setMainTractor(Tractor<RailTrack> tractor) {
        this.mainTractor = tractor;
    }
}
