package letrain.itinerary.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;

public record WaypointImpl(Waypoint.Type type, int targetId, Optional<Dir> entryDir,
        List<WaypointCommand> commands, Optional<LocalTime> arrival, Optional<LocalTime> departure)
        implements Waypoint {

    public WaypointImpl(Waypoint.Type type, int targetId, List<WaypointCommand> commands) {
        this(type, targetId, Optional.empty(), commands, Optional.empty(), Optional.empty());
    }

    public WaypointImpl(Waypoint.Type type, int targetId, Dir entryDir,
            List<WaypointCommand> commands) {
        this(type, targetId, Optional.ofNullable(entryDir), commands, Optional.empty(),
                Optional.empty());
    }

    public WaypointImpl(Waypoint.Type type, int targetId, Optional<Dir> entryDir,
            List<WaypointCommand> commands) {
        this(type, targetId, entryDir, commands, Optional.empty(), Optional.empty());
    }
}
