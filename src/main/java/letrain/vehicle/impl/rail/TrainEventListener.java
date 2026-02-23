package letrain.vehicle.impl.rail;

import java.io.Serializable;

public interface TrainEventListener extends Serializable {
    default public void onSpeedChanged(int speed) {
    }

    default public void onSenseChanged(boolean forward) {
    }

    default public void onCrash() {
    }

    default public void onContact() {
    }

    default public void onLink() {
    }
}
