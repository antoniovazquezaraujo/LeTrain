package letrain.itinerary;

import java.util.List;
import java.util.Optional;
import letrain.map.Dir;

/**
 * A waypoint in an itinerary. Can be a station or a sensor, with optional
 * entry direction and commands to execute on arrival.
 */
public record Waypoint(
    Type type,
    int targetId,
    Optional<Dir> entryDir,
    List<WaypointCommand> commands
) {
    public enum Type { STATION, SENSOR }

    public Waypoint(Type type, int targetId, List<WaypointCommand> commands) {
        this(type, targetId, Optional.empty(), commands);
    }

    public Waypoint(Type type, int targetId, Dir entryDir, List<WaypointCommand> commands) {
        this(type, targetId, Optional.of(entryDir), commands);
    }
}
