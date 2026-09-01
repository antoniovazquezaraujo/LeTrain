package letrain.vehicle.rail.impl;

import java.time.LocalDateTime;

public record Stop(int stationId, LocalDateTime stopTime, int distanceTraveled) {}
;
