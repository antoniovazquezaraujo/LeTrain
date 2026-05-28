package letrain.vehicle.rail;

import letrain.track.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface TrainMovementManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainMovementManager.class);

    boolean moveLinkers(boolean isNormalSense);

    void crash(Linker linker, int speed);

    void correctDirection(Linker linker);

    void clearReservations(List<Track> reservedTracks);
}
