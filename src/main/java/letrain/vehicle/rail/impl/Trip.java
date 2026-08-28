package letrain.vehicle.rail.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Trip implements letrain.vehicle.rail.Trip {
    @com.fasterxml.jackson.annotation.JsonProperty("stops")
    private List<Stop> stops;

    @com.fasterxml.jackson.annotation.JsonProperty("state")
    private TripState state;

    public Trip() {
        this.stops = new ArrayList<>();
        this.state = TripState.CONSTRUCTED;
    }

    @Override
    public void setStops(List<Stop> stops) {
        this.stops = stops != null ? stops : new ArrayList<>();
    }

    @Override
    public void setState(TripState state) {
        this.state = state;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Stop getFirstStop() {
        if (stops == null || stops.isEmpty()) {
            return null;
        }
        return stops.get(0);
    }

    @Override
    public void addStop(Stop stop) {
        if (stops == null) {
            stops = new ArrayList<>();
        }
        if (getStops().map(Stop::stationId).filter(t -> t == stop.stationId()).findFirst()
                .isPresent()) {
            stops.add(stop);
            this.state = TripState.AT_END;
        } else {
            if (stops.isEmpty()) {
                this.state = TripState.STARTING;
            } else {
                this.state = TripState.STOPPING;
            }
            stops.add(stop);
        }
    }

    @Override
    public void restart(Stop stop) {
        if (stops != null) {
            stops.clear();
        } else {
            stops = new ArrayList<>();
        }
        addStop(stop);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public List<Stop> getStopsList() {
        return stops;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Stream<Stop> getStops() {
        if (stops == null) {
            stops = new ArrayList<>();
        }
        return stops.stream();
    }

    @Override
    public TripState getState() {
        return this.state;
    }
}
