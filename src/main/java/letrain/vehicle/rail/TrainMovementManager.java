package letrain.vehicle.rail;

import java.util.List;

import letrain.track.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TrainMovementManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainMovementManager.class);

    boolean moveLinkers(boolean isNormalSense);

    void crash(Linker linker, int speed);

    void correctDirection(Linker linker);

    void clearReservations(List<Track> reservedTracks);

    void forceEmergencyStop();

    boolean advance();

    void refreshLinkersDirection();

    void initiateBraking();

    void restoreSpeed(int speed);
}
