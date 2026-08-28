package letrain.itinerary.impl;

import java.util.List;
import java.util.Optional;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;

public record WaypointImpl(Waypoint.Type type, int targetId, Optional<Dir> entryDir, List<WaypointCommand> commands)
        implements Waypoint {

    public WaypointImpl(Waypoint.Type type, int targetId, List<WaypointCommand> commands) {
        this(type, targetId, Optional.empty(), commands);
    }

    public WaypointImpl(Waypoint.Type type, int targetId, Dir entryDir, List<WaypointCommand> commands) {
        this(type, targetId, Optional.ofNullable(entryDir), commands);
    }
}
