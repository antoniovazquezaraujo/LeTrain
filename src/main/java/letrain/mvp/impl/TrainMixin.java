package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import letrain.itinerary.AutoPilot;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.TrainLogisticsManager;
import letrain.vehicle.rail.Trip;
import letrain.vehicle.rail.impl.Locomotive;

import java.util.Deque;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class TrainMixin {
    @JsonProperty("id")
    int id;

    @JsonProperty("name")
    String name;

    @JsonProperty("railStationId")
    int railStationId;

    @JsonProperty("stalled")
    boolean stalled;

    @JsonProperty("loadingCount")
    int loadingCount;

    @JsonProperty("linkers")
    @JsonDeserialize(as = java.util.LinkedList.class)
    Deque<Linker> linkers;

    @JsonProperty("logisticsManager")
    @JsonUnwrapped
    TrainLogisticsManager logisticsManager;

    @JsonProperty("trip")
    @JsonAlias({"itinerary", "trip"})
    @JsonDeserialize(as = letrain.vehicle.rail.impl.Trip.class)
    Trip trip;

    @JsonProperty("directorLinker")
    @JsonDeserialize(as = Locomotive.class)
    Tractor directorLinker;

    @JsonProperty("autopilot")
    AutoPilot autopilot;
}
