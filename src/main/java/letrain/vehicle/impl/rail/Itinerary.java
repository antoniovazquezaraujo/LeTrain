package letrain.vehicle.impl.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Itinerary {
    @com.fasterxml.jackson.annotation.JsonProperty("stops")
    List<Stop> stops;

    public enum ItineraryState {
        CONSTRUCTED,
        STARTING,
        STOPPING,
        AT_END
    }

    @com.fasterxml.jackson.annotation.JsonProperty("state")
    ItineraryState state;

    public Itinerary() {
        this.stops = new ArrayList<>();
        this.state = ItineraryState.CONSTRUCTED;
    }

    public void setStops(List<Stop> stops) {
        this.stops = stops;
    }

    public void setState(ItineraryState state) {
        this.state = state;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Stop getFirstStop() {
        if (stops == null || stops.isEmpty()) return null;
        return stops.get(0);
    }

    public void addStop(Stop stop) {
        if (getStops()
                .map(Stop::stationId)
                .filter(t -> t == stop.stationId())
                .findFirst()
                .isPresent()) {
            stops.add(stop);
            this.state = ItineraryState.AT_END;
        } else {
            if (stops.isEmpty()) {
                this.state = ItineraryState.STARTING;
            } else {
                this.state = ItineraryState.STOPPING;
            }
            stops.add(stop);
        }
    }

    public void restart(Stop stop) {
        if (stops != null) {
            stops.clear();
        } else {
            stops = new ArrayList<>();
        }
        addStop(stop);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<Stop> getStopsList() {
        return stops;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Stream<Stop> getStops() {
        return stops.stream();
    }

    public ItineraryState getState() {
        return this.state;
    }

}