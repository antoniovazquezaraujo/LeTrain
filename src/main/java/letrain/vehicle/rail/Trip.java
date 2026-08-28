package letrain.vehicle.rail;

import java.util.List;
import java.util.stream.Stream;
import letrain.vehicle.rail.impl.Stop;

@com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = letrain.vehicle.rail.impl.Trip.class)
public interface Trip {
    void setStops(List<Stop> stops);

    void setState(TripState state);

    @com.fasterxml.jackson.annotation.JsonIgnore
    Stop getFirstStop();

    void addStop(Stop stop);

    void restart(Stop stop);

    @com.fasterxml.jackson.annotation.JsonIgnore
    List<Stop> getStopsList();

    @com.fasterxml.jackson.annotation.JsonIgnore
    Stream<Stop> getStops();

    TripState getState();

    public enum TripState {
        CONSTRUCTED,
        STARTING,
        STOPPING,
        AT_END
    }
}
