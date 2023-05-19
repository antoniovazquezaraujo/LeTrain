package letrain;

import java.io.Serializable;
import java.util.function.Consumer;

import letrain.utils.Pair;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Locomotive.SpeedLimitType;
import letrain.vehicle.impl.rail.Train;

public class TrainSpeedConsumer implements Serializable, Consumer<Pair<Train, Pair<SpeedLimitType, Integer>>> {
    private static final long serialVersionUID = 1L;

    @Override
    public void accept(Pair<Train, Pair<SpeedLimitType, Integer>> input) {
        Locomotive locomotive = (Locomotive) (input.getFirst().getDirectorLinker());
        Integer speed = input.getSecond().getSecond();
        if (input.getSecond().getFirst() == SpeedLimitType.MAX_SPEED) {
            locomotive.setMaxSpeed(speed);
        } else {
            locomotive.setMinSpeed(speed);
        }
    }
}