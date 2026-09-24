package letrain.itinerary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * ADR-022 phase 2b: punctuality history of one train service, measured in <b>game minutes</b>.
 *
 * <p>
 * <b>Sign convention:</b> {@code +} = late, {@code −} = early ({@code +2} means two minutes behind
 * schedule, {@code -1} one minute ahead). One entry is recorded per scheduled stop: {@code arrival}
 * is measured when the waypoint is reached and {@code departure} when the retention releases the
 * train (or immediately when the train is already late). A stop may have only one of the two.
 *
 * <p>
 * <b>Aggregates</b> (kept simple and explained in the ADR):
 *
 * <ul>
 * <li><b>current</b>: the most recently measured delta (the last stop's departure if it has one,
 * otherwise its arrival);
 * <li><b>average</b> and <b>max</b>: over <b>all measured deltas</b>, arrivals and departures
 * counted individually. The max is the maximum <b>signed</b> delta, so with only early measurements
 * it is the least early one (e.g. {@code -1}).
 * </ul>
 *
 * <p>
 * Exact zero is printed without a sign ({@code 0 min}, not {@code +0 min}).
 *
 * <p>
 * The history is deterministic (derived only from ticks and the schedule) and lives in memory: it
 * is not part of the savegame, so loading a game restarts the service and its history. Text
 * formatting uses {@code Locale.ROOT} so {@code info train} is reproducible.
 */
public final class Punctuality {

    /** One scheduled stop: either delta (or both) may be present; at least one always is. */
    public record Stop(Waypoint.Type type, int targetId, Integer arrivalDelta,
            Integer departureDelta) {

        public Stop {
            if (arrivalDelta == null && departureDelta == null) {
                throw new IllegalArgumentException("a stop needs at least one delta");
            }
        }

        public OptionalInt arrival() {
            return arrivalDelta == null ? OptionalInt.empty() : OptionalInt.of(arrivalDelta);
        }

        public OptionalInt departure() {
            return departureDelta == null ? OptionalInt.empty() : OptionalInt.of(departureDelta);
        }
    }

    private final List<Stop> stops = new ArrayList<>();

    /** Records a measured arrival delta (game minutes; {@code +} late). */
    public void recordArrival(Waypoint.Type type, int targetId, int deltaMinutes) {
        stops.add(new Stop(type, targetId, deltaMinutes, null));
    }

    /**
     * Records a measured departure delta (game minutes; {@code +} late). It completes the last stop
     * when that stop is the same waypoint and has no departure yet; otherwise it opens a new stop
     * (a waypoint with a departure but no arrival).
     */
    public void recordDeparture(Waypoint.Type type, int targetId, int deltaMinutes) {
        if (!stops.isEmpty()) {
            Stop last = stops.get(stops.size() - 1);
            if (last.departureDelta() == null && last.type() == type
                    && last.targetId() == targetId) {
                stops.set(stops.size() - 1,
                        new Stop(type, targetId, last.arrivalDelta(), deltaMinutes));
                return;
            }
        }
        stops.add(new Stop(type, targetId, null, deltaMinutes));
    }

    /** Forgets the whole history (a new itinerary is a new service). */
    public void clear() {
        stops.clear();
    }

    public boolean isEmpty() {
        return stops.isEmpty();
    }

    /** Stops in chronological order. */
    public List<Stop> stops() {
        return Collections.unmodifiableList(new ArrayList<>(stops));
    }

    /** Most recently measured delta (departure of the last stop, else its arrival). */
    public OptionalInt currentDelta() {
        for (int i = stops.size() - 1; i >= 0; i--) {
            Stop stop = stops.get(i);
            if (stop.departureDelta() != null) {
                return OptionalInt.of(stop.departureDelta());
            }
            if (stop.arrivalDelta() != null) {
                return OptionalInt.of(stop.arrivalDelta());
            }
        }
        return OptionalInt.empty();
    }

    /** Arithmetic mean of all measured deltas (arrivals and departures). */
    public OptionalDouble averageDelta() {
        long sum = 0;
        int count = 0;
        for (Stop stop : stops) {
            if (stop.arrivalDelta() != null) {
                sum += stop.arrivalDelta();
                count++;
            }
            if (stop.departureDelta() != null) {
                sum += stop.departureDelta();
                count++;
            }
        }
        return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / (double) count);
    }

    /** Largest measured delta (worst delay when positive, smallest advance when negative). */
    public OptionalInt maxDelta() {
        OptionalInt max = OptionalInt.empty();
        for (Stop stop : stops) {
            if (stop.arrivalDelta() != null) {
                max = OptionalInt.of(max.isPresent() ? Math.max(max.getAsInt(), stop.arrivalDelta())
                        : stop.arrivalDelta());
            }
            if (stop.departureDelta() != null) {
                max = OptionalInt
                        .of(max.isPresent() ? Math.max(max.getAsInt(), stop.departureDelta())
                                : stop.departureDelta());
            }
        }
        return max;
    }

    /**
     * Human-readable block for {@code info train N} (empty when nothing has been measured). One
     * line per stop plus current/average/max. Deterministic ({@code Locale.ROOT}).
     */
    public String describe() {
        if (stops.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Punctuality:\n");
        for (Stop stop : stops) {
            sb.append("  ").append(label(stop)).append(": ");
            List<String> parts = new ArrayList<>();
            if (stop.arrivalDelta() != null) {
                parts.add("arrival " + signed(stop.arrivalDelta()) + " min");
            }
            if (stop.departureDelta() != null) {
                parts.add("departure " + signed(stop.departureDelta()) + " min");
            }
            sb.append(String.join(", ", parts)).append('\n');
        }
        sb.append("  Current: ").append(signed(currentDelta().orElseThrow())).append(" min\n");
        double average = averageDelta().orElseThrow();
        sb.append("  Average: ")
                .append(String.format(Locale.ROOT, average > 0 ? "+%.1f" : "%.1f", average))
                .append(" min\n");
        sb.append("  Max: ").append(signed(maxDelta().orElseThrow())).append(" min\n");
        return sb.toString();
    }

    private static String label(Stop stop) {
        return (stop.type() == Waypoint.Type.STATION ? "Station " : "Sensor ") + stop.targetId();
    }

    /** Signed delta: {@code +2} late, {@code -1} early, exact {@code 0} without sign. */
    private static String signed(int deltaMinutes) {
        return deltaMinutes > 0 ? "+" + deltaMinutes : Integer.toString(deltaMinutes);
    }
}
