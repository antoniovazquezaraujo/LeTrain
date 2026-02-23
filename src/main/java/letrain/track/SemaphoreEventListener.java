package letrain.track;

import java.io.Serializable;

public interface SemaphoreEventListener extends Serializable {
    default public void onOpen() {
    }

    default public void onClosed() {
    }
}
