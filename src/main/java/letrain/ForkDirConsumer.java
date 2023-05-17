package letrain;

import java.io.Serializable;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;

public class ForkDirConsumer implements Serializable, Consumer<Pair<ForkRailTrack, Dir>> {
    private static final long serialVersionUID = 1L;

    @Override
    public void accept(Pair<ForkRailTrack, Dir> input) {
        if (input.getFirst().getAlternativeRoute().getSecond().equals(input.getSecond())) {
            input.getFirst().setAlternativeRoute();
        } else {
            input.getFirst().setNormalRoute();
        }
    }
}