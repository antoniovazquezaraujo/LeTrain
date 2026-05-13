package letrain.itinerary;

/**
 * A command to execute when a waypoint is reached.
 * Simple commands (LOAD, UNLOAD, REVERSE, NONE) are constants.
 * Parameterized commands (WAIT, SPEED) use factory methods.
 */
public class WaypointCommand {
    public enum Kind { LOAD, UNLOAD, REVERSE, WAIT, SPEED, NONE }

    public static final WaypointCommand LOAD    = new WaypointCommand(Kind.LOAD);
    public static final WaypointCommand UNLOAD  = new WaypointCommand(Kind.UNLOAD);
    public static final WaypointCommand REVERSE = new WaypointCommand(Kind.REVERSE);
    public static final WaypointCommand NONE    = new WaypointCommand(Kind.NONE);

    private final Kind kind;
    private final int ticks;
    private final int targetSpeed;

    private WaypointCommand(Kind kind) {
        this(kind, 0, 0);
    }

    private WaypointCommand(Kind kind, int ticks, int targetSpeed) {
        this.kind = kind;
        this.ticks = ticks;
        this.targetSpeed = targetSpeed;
    }

    public static WaypointCommand waitTicks(int ticks) {
        return new WaypointCommand(Kind.WAIT, ticks, 0);
    }

    public static WaypointCommand speed(int targetSpeed) {
        return new WaypointCommand(Kind.SPEED, 0, targetSpeed);
    }

    public Kind kind()        { return kind; }
    public int ticks()        { return ticks; }
    public int targetSpeed()  { return targetSpeed; }

    public boolean isReverse() { return kind == Kind.REVERSE; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WaypointCommand c)) return false;
        return kind == c.kind && ticks == c.ticks && targetSpeed == c.targetSpeed;
    }

    @Override
    public int hashCode() { return kind.hashCode() ^ ticks ^ targetSpeed; }

    @Override
    public String toString() {
        return switch (kind) {
            case WAIT -> "WAIT(" + ticks + ")";
            case SPEED -> "SPEED(" + targetSpeed + ")";
            default -> kind.name();
        };
    }
}
