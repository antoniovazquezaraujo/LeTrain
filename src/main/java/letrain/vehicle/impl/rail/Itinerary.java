package letrain.vehicle.impl.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Itinerary {
    List<Stop> stops;

    public enum ItineraryState {
        CONSTRUCTED,
        STARTING,
        STOPPING,
        AT_END
    }

    ItineraryState state;

    public Itinerary() {
        this.stops = new ArrayList<>();
        this.state = ItineraryState.CONSTRUCTED;
    }

    public Stop getFirstStop() {
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
        stops.clear();
        addStop(stop);
    }

    public Stream<Stop> getStops() {
        return stops.stream();
    }

    public ItineraryState getState() {
        return this.state;
    }

}