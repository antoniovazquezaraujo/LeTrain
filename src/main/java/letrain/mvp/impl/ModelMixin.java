package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.impl.RailMap;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.TrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Wagon;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class ModelMixin {
    @JsonProperty("economyManager")
    @JsonDeserialize(as = letrain.economy.impl.EconomyManager.class)
    EconomyManager economyManager;

    @JsonProperty("selectedLocomotive")
    Locomotive selectedLocomotive;

    @JsonProperty("selectedFork")
    ForkRailTrack selectedFork;

    @JsonProperty("selectedSemaphore")
    RailSemaphore selectedSemaphore;

    @JsonProperty("selectedStation")
    Station selectedStation;

    @JsonProperty("eventLogManager")
    EventLogManager eventLogManager;

    @JsonProperty("selectedLocomotiveIndex")
    int selectedLocomotiveIndex;

    @JsonProperty("selectedForkIndex")
    int selectedForkIndex;

    @JsonProperty("selectedSemaphoreIndex")
    int selectedSemaphoreIndex;

    @JsonProperty("selectedStationIndex")
    int selectedStationIndex;

    @JsonProperty("showId")
    boolean showId;

    @JsonProperty("groundMap")
    @JsonDeserialize(as = letrain.ground.impl.GroundMap.class)
    GroundMap groundMap;

    @JsonProperty("mode")
    Model.GameMode mode;

    @JsonProperty("railMap")
    @com.fasterxml.jackson.annotation.JsonAlias({"map", "railMap"})
    RailMap map;

    @JsonProperty("locomotives")
    List<Locomotive> locomotives;

    @JsonProperty("wagons")
    List<Wagon> wagons;

    @JsonProperty("cursor")
    Cursor cursor;

    @JsonProperty("forks")
    List<ForkRailTrack> forks;

    @JsonProperty("sensors")
    List<Sensor> sensors;

    @JsonProperty("semaphores")
    List<RailSemaphore> semaphores;

    @JsonProperty("stations")
    List<Station> stations;

    @JsonProperty("nextLocomotiveId")
    int nextLocomotiveId;

    @JsonProperty("nextForkId")
    int nextForkId;

    @JsonIgnore
    CargoTypes selectedWagonType;

    @JsonIgnore
    com.badlogic.gdx.graphics.Camera camera;

    @JsonIgnore
    List<TrainEventListener> trainEventListeners;

    @JsonProperty("nextSensorId")
    int nextSensorId;

    @JsonProperty("nextSemaphoreId")
    int nextSemaphoreId;

    @JsonProperty("nextTrainId")
    int nextTrainId;

    @JsonProperty("nextStationId")
    int nextStationId;

    @JsonProperty("program")
    String program;

    @JsonProperty("seed")
    int seed;

    @JsonProperty("quantifier")
    int quantifier;

    @JsonProperty("quantifierSteps")
    int quantifierSteps;

    @JsonProperty("lastSaveTime")
    @JsonDeserialize(using = com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer.class)
    @JsonSerialize(using = com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer.class)
    LocalDateTime lastSaveTime;

    @JsonIgnore
    Object automationEngine;

    @JsonIgnore
    Object internalSimService;
}
