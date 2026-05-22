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
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.TrainLogisticsManager;
import letrain.vehicle.impl.rail.Trip;

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
    Trip trip;

    @JsonProperty("directorLinker")
    @JsonDeserialize(as = letrain.vehicle.impl.rail.Locomotive.class)
    Tractor directorLinker;

    @JsonProperty("autopilot")
    AutoPilot autopilot;

    @JsonProperty("autoMode")
    boolean autoMode;
}
