package letrain.itinerary;

/**
 * One-shot "advance until ... and stop" order (issue #619): the train starts, drives to the
 * destination and ends stopped. Unlike an itinerary it is not a plan that repeats, it is a single
 * maneuver.
 *
 * <p>
 * The five destinations are a station, a sensor, the end of the track, the first block that stops
 * the train ({@code stop when blocked}) and the vehicle ahead ({@code stop on contact}, the
 * coupling approach of issue #645). The speed belongs to the order: it is applied when the mission
 * starts ({@code 0} means "use the train's current target speed").
 */
public final class TrainMission {

    public enum Kind {
        STATION, SENSOR, END_OF_TRACK, WHEN_BLOCKED, ON_CONTACT
    }

    public enum State {
        ACTIVE, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Where the order comes from. A loose order (console/script) may auto-reverse once and returns
     * the train to manual when it ends; an itinerary maneuver (ADR-022 phase 2f waypoint action)
     * uses the current sense (the author writes the {@code reverse}) and keeps the autopilot
     * following the plan.
     */
    public enum Origin {
        LOOSE, ITINERARY
    }

    private final Kind kind;
    /** Station/sensor id; unused ({@code -1}) for END_OF_TRACK, WHEN_BLOCKED and ON_CONTACT. */
    private final int targetId;
    /** Order speed, or 0 to keep the train's current target. */
    private final int speed;
    private final Origin origin;
    private State state = State.ACTIVE;

    private TrainMission(Kind kind, int targetId, int speed, Origin origin) {
        this.kind = kind;
        this.targetId = targetId;
        this.speed = speed;
        this.origin = origin;
    }

    public static TrainMission stopAtStation(int stationId, int speed) {
        return new TrainMission(Kind.STATION, stationId, speed, Origin.LOOSE);
    }

    public static TrainMission stopAtSensor(int sensorId, int speed) {
        return new TrainMission(Kind.SENSOR, sensorId, speed, Origin.LOOSE);
    }

    public static TrainMission stopAtEndOfTrack(int speed) {
        return new TrainMission(Kind.END_OF_TRACK, -1, speed, Origin.LOOSE);
    }

    public static TrainMission stopWhenBlocked(int speed) {
        return new TrainMission(Kind.WHEN_BLOCKED, -1, speed, Origin.LOOSE);
    }

    /**
     * Issue #645: coupling approach. The mission drives at the ordered speed until the first
     * physical contact with the vehicle ahead (or the buffer ahead) and ends stopped, pressed
     * against it, ready for {@code couple}. At or above the crash threshold the contact is a crash
     * (the normal physics: no magic shield).
     */
    public static TrainMission stopOnContact(int speed) {
        return new TrainMission(Kind.ON_CONTACT, -1, speed, Origin.LOOSE);
    }

    /**
     * A waypoint action mission (ADR-022 phase 2f): no auto-reversal and the autopilot keeps
     * following the itinerary when the maneuver ends.
     */
    public static TrainMission forItinerary(Kind kind, int targetId, int speed) {
        return new TrainMission(kind, targetId, speed, Origin.ITINERARY);
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

    public Origin origin() {
        return origin;
    }

    public boolean isItineraryManeuver() {
        return origin == Origin.ITINERARY;
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
            case ON_CONTACT -> "the vehicle ahead";
        };
    }

    @Override
    public String toString() {
        return "TrainMission[" + description() + " speed=" + speed + " " + state + "]";
    }
}
