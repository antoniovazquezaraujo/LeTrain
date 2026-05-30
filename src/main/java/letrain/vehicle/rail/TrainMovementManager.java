package letrain.vehicle.rail;

import java.util.List;

import letrain.track.Track;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TrainMovementManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainMovementManager.class);

    boolean moveLinkers(boolean isNormalSense);

    void crash(Linker linker, int speed);

    void correctDirection(Linker linker);

    void clearReservations(List<Track> reservedTracks);

    void forceEmergencyStop();

    default void initiateBraking(Train train) {
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            int currentTargetSpeed = head.getTargetSpeed();
            Train.log.info("Train {} initiateBraking: target speed was {}, setting to 0", train.id, currentTargetSpeed);
            if (train.safetyManager != null) {
                train.safetyManager.onBrakingInitiated(currentTargetSpeed);
            }
            head.setTargetSpeed(0);
        }
    }

    default void restoreSpeed(int speed, Train train) {
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            Train.log.info("Train {} restoreSpeed: restoring target speed to {}", train.id, speed);
            head.setTargetSpeed(speed);
        }
    }
}
