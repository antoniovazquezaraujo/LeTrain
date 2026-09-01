package letrain.itinerary;

import java.util.List;
import java.util.Optional;
import letrain.map.Dir;

public interface Waypoint {
    enum Type {
        STATION, SENSOR
    }

    Type type();

    int targetId();

    Optional<Dir> entryDir();

    List<WaypointCommand> commands();
}
