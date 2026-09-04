package letrain.track;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import letrain.vehicle.rail.impl.Train;

/**
 * Represents an element that can be attached to a track, such as a Semaphore, Sensor, or Station.
 * A track can only hold a single component at a time to prevent overlapping.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = letrain.track.Sensor.class, name = "Sensor"),
    @JsonSubTypes.Type(value = letrain.track.Station.class, name = "Station"),
    @JsonSubTypes.Type(value = letrain.track.SpeedSignal.class, name = "SpeedSignal"),
    @JsonSubTypes.Type(value = letrain.track.RailSemaphore.class, name = "RailSemaphore")
    // Classes will be added here as we migrate them (e.g. Sensor, RailSemaphore)
})
public interface TrackComponent {

    /**
     * Called when a train's head enters the track containing this component.
     * @param train The train entering the track.
     */
    default void onTrainEnter(Train train) {}

    /**
     * Called when a train's tail leaves the track containing this component.
     * @param train The train leaving the track.
     */
    default void onTrainLeave(Train train) {}

    /**
     * Called every tick of the game loop.
     */
    default void onTick() {}
}
