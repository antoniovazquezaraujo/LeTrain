package letrain.itinerary;

/**
 * A command to execute when a waypoint is reached. Simple commands (LOAD, UNLOAD, REVERSE, STOP,
 * PARK, NONE) are constants. Parameterized commands use factory methods.
 *
 * <p>
 * ADR-022 phase 2f adds the script train orders as waypoint actions:
 * {@code couple}/{@code uncouple}, movement missions ({@code stop at …} /
 * {@code stop when blocked …}) and fork actions ({@code fork N set straight|curved},
 * {@code fork N flip}).
 */
public class WaypointCommand {
    public enum Kind {
        LOAD, UNLOAD, REVERSE, WAIT, SPEED, STOP, PARK, NONE, COUPLE, UNCOUPLE, MISSION, FORK_SET_DIRECTION, FORK_FLIP
    }

    /** Simulation ticks per second. Used to convert seconds ↔ ticks. */
    public static final int TICKS_PER_SECOND = 20;

    public static final WaypointCommand LOAD = new WaypointCommand(Kind.LOAD);
    public static final WaypointCommand UNLOAD = new WaypointCommand(Kind.UNLOAD);
    public static final WaypointCommand REVERSE = new WaypointCommand(Kind.REVERSE);
    public static final WaypointCommand STOP = new WaypointCommand(Kind.STOP);
    /**
     * Brakes and turns the engine off but keeps the autopilot; a scheduled departure restarts it.
     */
    public static final WaypointCommand PARK = new WaypointCommand(Kind.PARK);
    public static final WaypointCommand NONE = new WaypointCommand(Kind.NONE);

    private final Kind kind;
    private final int seconds;
    private final int targetSpeed;
    /** Couple/uncouple: direction is the physical front when true. */
    private final boolean forward;
    /** Couple/uncouple: 0 means all (couple) or one (uncouple), like the direct orders. */
    private final int count;
    /** Mission destination kind for MISSION commands. */
    private final TrainMission.Kind missionKind;
    /** Station/sensor id for MISSION commands ({@code -1} for end of track / blocked). */
    private final int targetId;
    private final int forkId;
    /** {@code straight}, {@code curved} or a compass direction for FORK_SET_DIRECTION. */
    private final String forkDirection;

    private WaypointCommand(Kind kind) {
        this(kind, 0, 0, false, 0, null, -1, -1, null);
    }

    private WaypointCommand(Kind kind, int seconds, int targetSpeed) {
        this(kind, seconds, targetSpeed, false, 0, null, -1, -1, null);
    }

    private WaypointCommand(Kind kind, int seconds, int targetSpeed, boolean forward, int count,
            TrainMission.Kind missionKind, int targetId, int forkId, String forkDirection) {
        this.kind = kind;
        this.seconds = seconds;
        this.targetSpeed = targetSpeed;
        this.forward = forward;
        this.count = count;
        this.missionKind = missionKind;
        this.targetId = targetId;
        this.forkId = forkId;
        this.forkDirection = forkDirection;
    }

    public static WaypointCommand waitSeconds(int seconds) {
        return new WaypointCommand(Kind.WAIT, seconds, 0);
    }

    public static WaypointCommand speed(int targetSpeed) {
        return new WaypointCommand(Kind.SPEED, 0, targetSpeed);
    }

    /**
     * Couples vehicles ahead/behind (ADR-022 phase 2f). {@code count} 0 means all linkable
     * vehicles, like the direct order.
     */
    public static WaypointCommand couple(boolean forward, int count) {
        return new WaypointCommand(Kind.COUPLE, 0, 0, forward, count, null, -1, -1, null);
    }

    /**
     * Uncouples vehicles ahead/behind (ADR-022 phase 2f). {@code count} is clamped to one minimum.
     */
    public static WaypointCommand uncouple(boolean forward, int count) {
        return new WaypointCommand(Kind.UNCOUPLE, 0, 0, forward, count, null, -1, -1, null);
    }

    /**
     * Movement mission executed at the waypoint (ADR-022 phase 2f): the train drives to the
     * destination and stops before the next action runs. Speed 0 means "keep the current target".
     * Waypoint missions do not auto-reverse: the author writes the {@code reverse} explicitly.
     */
    public static WaypointCommand mission(TrainMission.Kind missionKind, int targetId,
            int targetSpeed) {
        return new WaypointCommand(Kind.MISSION, 0, targetSpeed, false, 0, missionKind, targetId,
                -1, null);
    }

    /** Forces the switch route by geometry: {@code straight}, {@code curved} or a compass dir. */
    public static WaypointCommand forkSetDirection(int forkId, String direction) {
        return new WaypointCommand(Kind.FORK_SET_DIRECTION, 0, 0, false, 0, null, -1, forkId,
                direction);
    }

    /** Flips the switch route (ADR-022 phase 2f). */
    public static WaypointCommand forkFlip(int forkId) {
        return new WaypointCommand(Kind.FORK_FLIP, 0, 0, false, 0, null, -1, forkId, null);
    }

    public Kind kind() {
        return kind;
    }

    public int seconds() {
        return seconds;
    }

    public int targetSpeed() {
        return targetSpeed;
    }

    public boolean forward() {
        return forward;
    }

    public int count() {
        return count;
    }

    public TrainMission.Kind missionKind() {
        return missionKind;
    }

    public int targetId() {
        return targetId;
    }

    public int forkId() {
        return forkId;
    }

    public String forkDirection() {
        return forkDirection;
    }

    public boolean isReverse() {
        return kind == Kind.REVERSE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WaypointCommand c)) {
            return false;
        }
        return kind == c.kind && seconds == c.seconds && targetSpeed == c.targetSpeed
                && forward == c.forward && count == c.count && missionKind == c.missionKind
                && targetId == c.targetId && forkId == c.forkId
                && java.util.Objects.equals(forkDirection, c.forkDirection);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, seconds, targetSpeed, forward, count, missionKind,
                targetId, forkId, forkDirection);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case WAIT -> "WAIT(" + seconds + ")";
            case SPEED -> "SPEED(" + targetSpeed + ")";
            case COUPLE -> "COUPLE(" + (forward ? "forward" : "backward") + "," + count + ")";
            case UNCOUPLE -> "UNCOUPLE(" + (forward ? "forward" : "backward") + "," + count + ")";
            case MISSION -> "MISSION(" + missionKind + "," + targetId + "," + targetSpeed + ")";
            case FORK_SET_DIRECTION -> "FORK_SET(" + forkId + "," + forkDirection + ")";
            case FORK_FLIP -> "FORK_FLIP(" + forkId + ")";
            default -> kind.name();
        };
    }
}
