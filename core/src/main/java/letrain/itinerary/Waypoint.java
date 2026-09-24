package letrain.itinerary;

import java.time.LocalTime;
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

    /**
     * Scheduled arrival time of day (ADR-022 phase 2), measured when the waypoint is reached. Empty
     * means the waypoint has no schedule: it behaves exactly as before the timetable feature.
     */
    Optional<LocalTime> arrival();

    /**
     * Scheduled departure time of day (ADR-022 phase 2). The dwell at the waypoint is
     * {@code departure - arrival} in game time (see {@link Timetable#dwellMinutes}). Empty means the
     * train leaves as soon as its actions finish.
     */
    Optional<LocalTime> departure();
}
