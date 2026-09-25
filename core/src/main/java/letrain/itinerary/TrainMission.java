package letrain.itinerary;

/**
 * One-shot "advance until ... and stop" order (issue #619): the train starts, drives to the
 * destination and ends stopped. Unlike an itinerary it is not a plan that repeats, it is a single
 * maneuver.
 *
 * <p>
 * The four destinations are a station, a sensor, the end of the track and the first block that
 * stops the train ({@code stop when blocked}). The speed belongs to the order: it is applied when
 * the mission starts ({@code 0} means "use the train's current target speed").
 */
public final class TrainMission {

    public enum Kind {
        STATION, SENSOR, END_OF_TRACK, WHEN_BLOCKED
    }

    public enum State {
        ACTIVE, COMPLETED, FAILED, CANCELLED
    }

    private final Kind kind;
    /** Station/sensor id; unused ({@code -1}) for END_OF_TRACK and WHEN_BLOCKED. */
    private final int targetId;
    /** Order speed, or 0 to keep the train's current target. */
    private final int speed;
    private State state = State.ACTIVE;

    private TrainMission(Kind kind, int targetId, int speed) {
        this.kind = kind;
        this.targetId = targetId;
        this.speed = speed;
    }

    public static TrainMission stopAtStation(int stationId, int speed) {
        return new TrainMission(Kind.STATION, stationId, speed);
    }

    public static TrainMission stopAtSensor(int sensorId, int speed) {
        return new TrainMission(Kind.SENSOR, sensorId, speed);
    }

    public static TrainMission stopAtEndOfTrack(int speed) {
        return new TrainMission(Kind.END_OF_TRACK, -1, speed);
    }

    public static TrainMission stopWhenBlocked(int speed) {
        return new TrainMission(Kind.WHEN_BLOCKED, -1, speed);
    }

    public Kind kind() {
        return kind;
    }

    public int targetId() {
        return targetId;
    }

    /** Order speed, or 0 when the mission must use the train's current target speed. */
    public int speed() {
        return speed;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /** Marks the mission as running (a triggered order can be restarted). */
    public void start() {
        this.state = State.ACTIVE;
    }

    public void complete() {
        this.state = State.COMPLETED;
    }

    public void fail() {
        this.state = State.FAILED;
    }

    public void cancel() {
        this.state = State.CANCELLED;
    }

    /** Human-readable destination for messages and tests. */
    public String description() {
        return switch (kind) {
            case STATION -> "station " + targetId;
            case SENSOR -> "sensor " + targetId;
            case END_OF_TRACK -> "the end of track";
            case WHEN_BLOCKED -> "the first block";
        };
    }

    @Override
    public String toString() {
        return "TrainMission[" + description() + " speed=" + speed + " " + state + "]";
    }
}
