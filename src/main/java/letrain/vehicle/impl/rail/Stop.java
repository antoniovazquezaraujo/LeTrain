package letrain.vehicle.impl.rail;

import java.time.LocalDateTime;

public record Stop(int stationId, LocalDateTime stopTime, int distanceTraveled) {
};
