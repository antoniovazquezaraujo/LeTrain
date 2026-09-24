package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import letrain.itinerary.Itinerary;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2a: waypoint {@code arrival}/{@code departure} grammar and model. The grammar is
 * strict when writing: commas between plan items are mandatory and the order is {@code arrival}
 * first, actions, {@code departure} last. Times are stored in the waypoint; retention is phase 2b.
 */
@DisplayName("Timetable grammar and waypoint model (ADR-022 phase 2a)")
class TimetableGrammarTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station a = new Station(1);
        a.setName("A");
        model.addStation(a);
        Station b = new Station(2);
        b.setName("B");
        model.addStation(b);
        Sensor sensor = new Sensor(1);
        sensor.setName("S1");
        model.addSensor(sensor);
        Locomotive loco = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(loco);
        model.addLocomotive(loco);
    }

    /** Runs a program and asserts it parsed with no diagnostics. */
    private void program(String text) {
        List<String> errors = model.setProgram(text);
        assertTrue(errors.isEmpty(), "unexpected parser errors: " + errors);
    }

    private Itinerary assignedItinerary() {
        Train train = model.getTrainFromLocomotiveId(1);
        return train.getAutopilot().itinerary().orElseThrow();
    }

    @Nested
    @DisplayName("Accepts the new syntax")
    class AcceptsNewSyntax {

        @Test
        @DisplayName("arrival, actions and departure with commas are stored in the waypoint")
        void fullPlan_isStoredInOrder() {
            program("""
                    create itinerary "Ruta" {
                        add station 1 arrival 09:00, load, departure 09:20;
                        add station 2 arrival 10:23, unload, departure 10:30;
                    }
                    assign itinerary "Ruta" to train 1;
                    """);

            List<Waypoint> waypoints = assignedItinerary().waypoints();
            assertEquals(2, waypoints.size());

            Waypoint first = waypoints.get(0);
            assertEquals(LocalTime.of(9, 0), first.arrival().orElseThrow());
            assertEquals(LocalTime.of(9, 20), first.departure().orElseThrow());
            assertEquals(List.of(WaypointCommand.LOAD), first.commands());

            Waypoint second = waypoints.get(1);
            assertEquals(LocalTime.of(10, 23), second.arrival().orElseThrow());
            assertEquals(LocalTime.of(10, 30), second.departure().orElseThrow());
            assertEquals(List.of(WaypointCommand.UNLOAD), second.commands());
        }

        @Test
        @DisplayName("sensors accept times too; single-digit hours and direction still work")
        void sensorTimesAndSingleDigitHour_work() {
            program("""
                    create itinerary "Ruta" {
                        add station 1 e departure 6:05;
                        add sensor 1 arrival 7:00, wait 3;
                    }
                    assign itinerary "Ruta" to train 1;
                    """);

            List<Waypoint> waypoints = assignedItinerary().waypoints();
            Waypoint station = waypoints.get(0);
            assertEquals(Dir.E, station.entryDir().orElseThrow());
            assertEquals(LocalTime.of(6, 5), station.departure().orElseThrow());
            assertTrue(station.arrival().isEmpty());

            Waypoint sensor = waypoints.get(1);
            assertEquals(LocalTime.of(7, 0), sensor.arrival().orElseThrow());
            assertTrue(sensor.departure().isEmpty());
            assertEquals(WaypointCommand.Kind.WAIT, sensor.commands().get(0).kind());
        }

        @Test
        @DisplayName("a waypoint without times keeps behaving exactly as before")
        void noTimes_isStillValid() {
            program("""
                    create itinerary "Ruta" {
                        add station 1 load
                        add station 2 reverse, unload
                    }
                    assign itinerary "Ruta" to train 1;
                    """);

            List<Waypoint> waypoints = assignedItinerary().waypoints();
            assertTrue(waypoints.get(0).arrival().isEmpty());
            assertTrue(waypoints.get(1).arrival().isEmpty());
            assertTrue(waypoints.get(1).departure().isEmpty());
            assertEquals(List.of(WaypointCommand.REVERSE, WaypointCommand.UNLOAD),
                    waypoints.get(1).commands());
        }

        @Test
        @DisplayName("departure alone needs no comma")
        void departureAlone_isAccepted() {
            program("""
                    create itinerary "Ruta" {
                        add station 1 departure 6:00
                        add station 2 departure 23:50
                    }
                    assign itinerary "Ruta" to train 1;
                    """);

            assertEquals(LocalTime.of(6, 0), assignedItinerary().waypoints().get(0).departure()
                    .orElseThrow());
        }
    }

    @Nested
    @DisplayName("Rejects old/ambiguous syntax with a clear error")
    class RejectsInvalidSyntax {

        /** Runs an invalid waypoint line and returns the first parser diagnostic. */
        private String assertProgramRejected(String waypointLine) {
            List<String> errors = model.setProgram(
                    "create itinerary \"Ruta\" {\n    " + waypointLine + "\n}\n");
            assertFalse(errors.isEmpty(), "expected a syntax error for: " + waypointLine);
            return errors.get(0);
        }

        @Test
        @DisplayName("missing comma between actions")
        void missingComma_isRejected() {
            assertTrue(assertProgramRejected("add station 1 reverse unload").contains("unload"));
        }

        @Test
        @DisplayName("missing comma before departure")
        void missingCommaBeforeDeparture_isRejected() {
            assertTrue(assertProgramRejected("add station 1 load departure 9:20")
                    .contains("departure"));
        }

        @Test
        @DisplayName("arrival after an action (out of order)")
        void arrivalAfterAction_isRejected() {
            assertTrue(assertProgramRejected("add station 1 unload, arrival 10:23")
                    .contains("arrival"));
        }

        @Test
        @DisplayName("action after departure (out of order)")
        void actionAfterDeparture_isRejected() {
            assertTrue(assertProgramRejected("add station 1 departure 9:20, load")
                    .contains("line 2"), "the diagnostic must point at the offending line");
        }

        @Test
        @DisplayName("out-of-range times are not a TIME token")
        void outOfRangeTime_isRejected() {
            assertTrue(assertProgramRejected("add station 1 arrival 25:00").contains("TIME"));
            assertTrue(assertProgramRejected("add station 1 departure 9:60").contains("TIME"));
        }
    }
}
