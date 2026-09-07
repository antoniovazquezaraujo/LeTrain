package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for issue #468: moving a {@link Station} re-evaluates its industrial
 * role/cargo using the same industry-influence logic used at creation time
 * ({@code RailTrackMaker.manageStationSensor}, radius 5).
 *
 * <p>
 * These tests are RED by design: {@code Model.moveSensor} does not exist yet. The real
 * {@code GroundMap} is replaced by a Mockito mock through the existing {@code Model.setGroundMap}
 * seam so the tests stay deterministic (no Perlin noise).
 */
@DisplayName("Station move re-evaluates industry role (issue #468)")
class StationMoveRoleTest {

    private Model model;
    private GroundMap groundMap;

    @BeforeEach
    void setUp() {
        model = new Model();
        groundMap = mock(GroundMap.class);
        model.setGroundMap(groundMap);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RailTrack straightAt(int x) {
        RailTrack track = new RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, 0));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    private void connectEw(RailTrack west, RailTrack east) {
        west.connect(Dir.E, east);
        east.connect(Dir.W, west);
    }

    private Station stationOn(RailTrack track, int id) {
        Station station = new Station(id);
        station.setName("Station " + id);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    // ------------------------------------------------------------------
    // 6a. Moving AWAY from industry influence degrades the station
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6.1 Station moved out of industry influence degrades to GENERIC/NONE and loses cargo")
    void station_movedAwayFromIndustry_degradesToGeneric() {
        // Arrange: producer station at (0,0), free straight track at (1,0).
        RailTrack t0 = straightAt(0);
        RailTrack t1 = straightAt(1);
        connectEw(t0, t1);
        Station station = stationOn(t0, 1);
        station.setRole(CargoTypes.StationRole.PRODUCER);
        station.setCargoType(CargoTypes.COAL);
        station.setIndustryCount(3);
        station.setStorage(50);

        // The origin is inside a coal-mine zone; the destination is in the middle of nowhere.
        when(groundMap.findClosestIndustry(eq(t0.getPosition()), eq(5)))
                .thenReturn(GroundMap.MINE);
        when(groundMap.findClosestIndustry(eq(t1.getPosition()), eq(5))).thenReturn(null);

        // Act
        boolean moved = model.moveSensor(station, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t1, station.getTrack());
        assertSame(station, t1.getComponent());
        assertNull(t0.getComponent());
        assertEquals(CargoTypes.StationRole.GENERIC, station.getRole());
        assertEquals(CargoTypes.NONE, station.getCargoType());
        assertEquals(0, station.getStorage(), "station must lose its stored cargo when degraded");
        assertSame(station, model.getStation(1), "station must stay registered in the model");
    }

    // ------------------------------------------------------------------
    // 6b. Moving INTO industry influence restores the station role
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6.2 Generic station moved into a PRODUCER zone becomes PRODUCER with cargo and storage")
    void station_movedIntoProducerZone_recoversProducerRole() {
        // Arrange: generic station at (0,0), destination (1,0) inside a coal mine.
        RailTrack t0 = straightAt(0);
        RailTrack t1 = straightAt(1);
        connectEw(t0, t1);
        Station station = stationOn(t0, 2);
        station.setRole(CargoTypes.StationRole.GENERIC);
        station.setCargoType(CargoTypes.NONE);

        when(groundMap.findClosestIndustry(eq(t0.getPosition()), eq(5))).thenReturn(null);
        when(groundMap.findClosestIndustry(eq(t1.getPosition()), eq(5)))
                .thenReturn(GroundMap.MINE);
        when(groundMap.countIndustryDensity(eq(t1.getPosition()), eq(5), eq(GroundMap.MINE)))
                .thenReturn(2);

        // Act
        boolean moved = model.moveSensor(station, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t1, station.getTrack());
        assertEquals(CargoTypes.StationRole.PRODUCER, station.getRole());
        assertEquals(CargoTypes.COAL, station.getCargoType());
        assertEquals(2, station.getIndustryCount());
        assertEquals(50, station.getStorage());
        assertSame(station, model.getStation(2), "station must stay registered in the model");
    }

    @Test
    @DisplayName("6.3 Generic station moved into a CONSUMER zone becomes CONSUMER with cargo")
    void station_movedIntoConsumerZone_recoversConsumerRole() {
        // Arrange: generic station at (0,0), destination (1,0) inside a jewelry store.
        RailTrack t0 = straightAt(0);
        RailTrack t1 = straightAt(1);
        connectEw(t0, t1);
        Station station = stationOn(t0, 3);
        station.setRole(CargoTypes.StationRole.GENERIC);
        station.setCargoType(CargoTypes.NONE);
        station.setStorage(0);

        when(groundMap.findClosestIndustry(eq(t0.getPosition()), eq(5))).thenReturn(null);
        when(groundMap.findClosestIndustry(eq(t1.getPosition()), eq(5)))
                .thenReturn(GroundMap.JEWELRY_STORE);
        when(groundMap.countIndustryDensity(
                        eq(t1.getPosition()), eq(5), eq(GroundMap.JEWELRY_STORE)))
                .thenReturn(4);

        // Act
        boolean moved = model.moveSensor(station, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t1, station.getTrack());
        assertEquals(CargoTypes.StationRole.CONSUMER, station.getRole());
        assertEquals(CargoTypes.GOLD, station.getCargoType());
        assertEquals(4, station.getIndustryCount());
        assertEquals(0, station.getStorage());
        assertSame(station, model.getStation(3), "station must stay registered in the model");
    }
}
