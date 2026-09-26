package letrain.itinerary;

import java.util.List;
import java.util.Optional;
import letrain.segments.Segment;

/** Drives a train automatically along an itinerary. Called each tick by the locomotive. */
public interface AutoPilot {

    enum Mode {
        IDLE, FOLLOWING, WAITING, REVERSING, ERROR
    }

    /** The itinerary this autopilot is following. */
    Optional<Itinerary> itinerary();

    /** Current operational mode. */
    Mode mode();

    /** Assign an itinerary to follow. */
    void setItinerary(Itinerary itinerary);

    /** Start/activate the autopilot. Only works if a valid route exists. */
    boolean activate();

    /** Stop and return to manual control. */
    void deactivate();

    /**
     * Starts a one-shot mission (issue #619): drive to the destination and stop. Returns false
     * without touching the train when the order is rejected (the train is running an itinerary, the
     * destination is unreachable from both senses or there is no speed to run at); the reason is
     * reported through the mission notifier and the log. A new mission replaces the previous one.
     */
    default boolean startMission(TrainMission mission) {
        return false;
    }

    /** Last mission accepted by this autopilot (running or finished), if any. */
    default Optional<TrainMission> mission() {
        return Optional.empty();
    }

    /**
     * Called after every real rail advance, right after the safety hook (issue #619). Missions use
     * it to watch the destination and to apply the braking curve towards the stop point. No-op when
     * there is no mission running.
     */
    default void onRailAdvanced() {}

    /**
     * Segment the active mission drives to, if any (ADR-022 phase 2f). The safety layer uses it to
     * let a shunting maneuver enter the canton where its destination is (the wagons it must couple
     * to), instead of waiting forever for that block.
     */
    default java.util.Optional<Segment> missionTargetSegment() {
        return java.util.Optional.empty();
    }

    /**
     * One simulation tick of the train (issue #619). Missions use it as a stall watchdog: a mission
     * train stopped without a block/schedule/loading reason fails after a grace period. No-op when
     * there is no mission running.
     */
    default void onTick() {}

    /**
     * Sink for mission problem messages (rejection, unreachable, lost route, stall). The console
     * sets it so typed orders warn on screen; when it is not set, warnings fall back to the model's
     * user message sink (programs, loaded savegames) and only log when there is none (headless
     * contexts). Success notices are log-only and never reach this sink (issue #619).
     */
    default void setMissionNotifier(java.util.function.Consumer<String> notifier) {}

    /** The currently targeted waypoint. */
    Optional<Waypoint> currentWaypoint();

    /** Advances to the next waypoint in the itinerary. */
    void advanceWaypoint();

    /** The currently calculated route (segments from current position to next waypoint). */
    List<Segment> currentRoute();

    /** Current waypoint index within the itinerary. */
    int currentWaypointIndex();

    /** Set the pathfinder to use for route calculation. */
    void setPathfinder(SegmentPathfinder pathfinder);

    default void onSegmentEntered(Segment newSegment) {}

    void resumeWaiting();

    void clearRoute();

    /**
     * Replaces a segment in the current route (e.g. when bypassing a blocked segment with an
     * alternative).
     */
    void replaceRouteSegment(Segment oldSeg, Segment newSeg);

    /** Ensure the fork between 'from' and 'to' segments is oriented correctly. */
    default void ensureForkRoute(letrain.segments.Segment from, letrain.segments.Segment to) {}

    /**
     * Measures the arrival at a waypoint against its timetable (ADR-022 phase 2b). No-op when the
     * waypoint has no {@code arrival} time.
     */
    default void measureArrival(Waypoint waypoint) {}

    /**
     * Checks the current waypoint's {@code departure} time (ADR-022 phase 2b). When the train is
     * early it returns {@code true} and the autopilot switches to {@link Mode#WAITING} until the
     * departure (a deterministic tick-scheduled release). When the departure is due or already
     * passed it records the departure delta and returns {@code false}. No-op (returns false) when
     * there is no departure or no clock, so waypoints without times keep the old behaviour.
     */
    default boolean retainUntilDeparture() {
        return false;
    }

    /** Punctuality history of the current service (ADR-022 phase 2b). */
    default Optional<Punctuality> punctuality() {
        return Optional.empty();
    }
}
