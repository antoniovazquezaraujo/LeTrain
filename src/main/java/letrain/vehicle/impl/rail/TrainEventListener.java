package letrain.vehicle.impl.rail;

import java.io.Serializable;

public interface TrainEventListener extends Serializable {
    default public void onSpeedChanged(int speed) {
    }

    default public void onSenseChanged(boolean forward) {
    }

    default public void onCrash(Train train, letrain.map.Point pos) {
    }

    default public void onContact(letrain.map.Point pos) {
    }

    default public void onLink() {
    }
}
