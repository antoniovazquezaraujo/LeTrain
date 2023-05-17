package letrain;

import java.io.Serializable;
import java.util.function.Consumer;

import letrain.track.RailSemaphore;
import letrain.utils.Pair;

public class SemaphoreStateConsumer implements Serializable, Consumer<Pair<RailSemaphore, Boolean>> {
    private static final long serialVersionUID = 1L;

    @Override
    public void accept(Pair<RailSemaphore, Boolean> input) {
        input.getFirst().setOpen(input.getSecond());
    }
}