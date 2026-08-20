package letrain.itinerary;

/**
 * A command to execute when a waypoint is reached.
 * Simple commands (LOAD, UNLOAD, REVERSE, NONE) are constants.
 * Parameterized commands (WAIT, SPEED) use factory methods.
 */
public class WaypointCommand {
    public enum Kind { LOAD, UNLOAD, REVERSE, WAIT, SPEED, STOP, NONE }

    /** Simulation ticks per second. Used to convert seconds ↔ ticks. */
    public static final int TICKS_PER_SECOND = 20;

    public static final WaypointCommand LOAD    = new WaypointCommand(Kind.LOAD);
    public static final WaypointCommand UNLOAD  = new WaypointCommand(Kind.UNLOAD);
    public static final WaypointCommand REVERSE = new WaypointCommand(Kind.REVERSE);
    public static final WaypointCommand STOP    = new WaypointCommand(Kind.STOP);
    public static final WaypointCommand NONE    = new WaypointCommand(Kind.NONE);

    private final Kind kind;
    private final int seconds;
    private final int targetSpeed;

    private WaypointCommand(Kind kind) {
        this(kind, 0, 0);
    }

    private WaypointCommand(Kind kind, int seconds, int targetSpeed) {
        this.kind = kind;
        this.seconds = seconds;
        this.targetSpeed = targetSpeed;
    }

    public static WaypointCommand waitSeconds(int seconds) {
        return new WaypointCommand(Kind.WAIT, seconds, 0);
    }

    public static WaypointCommand speed(int targetSpeed) {
        return new WaypointCommand(Kind.SPEED, 0, targetSpeed);
    }

    public Kind kind()        { return kind; }
    public int seconds()      { return seconds; }
    public int targetSpeed()  { return targetSpeed; }

    public boolean isReverse() { return kind == Kind.REVERSE; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WaypointCommand c)) return false;
        return kind == c.kind && seconds == c.seconds && targetSpeed == c.targetSpeed;
    }

    @Override
    public int hashCode() { return kind.hashCode() ^ seconds ^ targetSpeed; }

    @Override
    public String toString() {
        return switch (kind) {
            case WAIT -> "WAIT(" + seconds + ")";
            case SPEED -> "SPEED(" + targetSpeed + ")";
            default -> kind.name();
        };
    }
}
