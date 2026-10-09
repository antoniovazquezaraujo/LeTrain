package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import letrain.mvp.impl.Model;
import letrain.time.GameTime;
import letrain.time.TemporalTrigger;
import letrain.time.TemporalTriggerService;
import letrain.track.RailSemaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 3, F3a: temporal triggers in the program (grammar, world registry and scheduling).
 * The block actions are not executed yet (that is F3b); these tests check that the trigger is
 * parsed, registered with the right shape and that its next fire is armed from the game clock.
 * Deterministic: only game ticks drive the clock.
 */
@DisplayName("Temporal trigger program: registry and scheduling (ADR-022 phase 3, F3a)")
class TemporalTriggerProgramTest {

    private Model model;
    private final List<String> notices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        model.addSemaphore(new RailSemaphore(1));
        // A wired sink is what makes the program warnings visible (clients wire it; headless tests
        // would only log).
        model.setUserMessageSink((title, text) -> notices.add(title + ": " + text));
    }

    private TemporalTriggerService service() {
        return model.getTemporalTriggerService();
    }

    /** Mirrors SimulationController order: scheduler first, then the game clock. */
    private void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            model.getScheduler().tick();
            model.getGameClock().tick();
        }
    }

    @Nested
    @DisplayName("Grammar and registry")
    class Registry {

        @Test
        @DisplayName("at HH:MM parses and registers a daily trigger")
        void atTrigger_isRegistered() {
            List<String> errors = model.setProgram("""
                    at 6:30 {
                        semaphore 1 open;
                    }
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 30))), service().triggers());
        }

        @Test
        @DisplayName("U8-style: a bare hour `at 6` means 06:00")
        void atBareHour_isRegisteredAsOClock() {
            List<String> errors = model.setProgram("at 6 {\n  semaphore 1 open;\n}");

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 0))), service().triggers());
        }

        @Test
        @DisplayName("every accepts m/h/d units and an optional from")
        void everyAllUnits_andFrom_areRegistered() {
            List<String> errors = model.setProgram("""
                    every 30m {
                        semaphore 1 open;
                    }
                    every 2h {
                        semaphore 1 close;
                    }
                    every 1d {
                        semaphore 1 invert;
                    }
                    every 45m from 6:15 {
                        train 5 set engine on;
                    }
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(List.of(TemporalTrigger.every(30), TemporalTrigger.every(120),
                    TemporalTrigger.every(1440), TemporalTrigger.every(45, LocalTime.of(6, 15))),
                    service().triggers());
        }

        @Test
        @DisplayName("a program with triggers and other statements still applies normally")
        void triggers_coexistWithOtherStatements() {
            model.addStation(new letrain.track.Station(1));
            List<String> errors = model.setProgram("""
                    at 6:30 {
                        semaphore 1 open;
                    }
                    create itinerary "Ruta" {
                        add station 1
                    }
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 30))), service().triggers());
        }

        @Test
        @DisplayName("nested blocks are rejected: the action block does not nest")
        void nestedBlocks_areSyntaxErrors() {
            List<String> errors =
                    model.setProgram("at 6:30 {\n  at 7:00 {\n    semaphore 1 open;\n  }\n}");

            assertFalse(errors.isEmpty(), "nested temporal blocks must not parse");
            assertTrue(service().triggers().isEmpty());
        }

        @Test
        @DisplayName("typed in the console, the trigger registers in the same world registry")
        void consoleTyping_registersTheTrigger() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));

            String error = PlayerCommandExecutor.execute("every 30m { semaphore 1 open; }", model);

            assertNull(error, error);
            assertEquals(List.of(TemporalTrigger.every(30)), service().triggers());
            assertEquals(460, service().ticksUntilNextFire(TemporalTrigger.every(30)));
        }
    }

    @Nested
    @DisplayName("Invalid periods and hours warn and are ignored")
    class Warnings {

        @Test
        @DisplayName("0m and an unknown unit are visible warnings, not syntax errors")
        void invalidPeriods_warnAndAreNotRegistered() {
            List<String> errors = model.setProgram("""
                    every 0m {
                        semaphore 1 open;
                    }
                    every 30x {
                        semaphore 1 open;
                    }
                    """);

            assertTrue(errors.isEmpty(), "semantic warnings do not reject the program: " + errors);
            assertTrue(service().triggers().isEmpty());
            assertTrue(notices.stream().anyMatch(n -> n.contains("Invalid period '0m'")),
                    notices.toString());
            assertTrue(notices.stream().anyMatch(n -> n.contains("Unknown period unit 'x'")),
                    notices.toString());
        }

        @Test
        @DisplayName("a bare hour out of 0..23 warns and is ignored (`at 25`)")
        void invalidHour_warnsAndIsNotRegistered() {
            List<String> errors = model.setProgram("at 25 {\n  semaphore 1 open;\n}");

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(service().triggers().isEmpty());
            assertTrue(notices.stream().anyMatch(n -> n.contains("'at 25' is out of range")),
                    notices.toString());
        }

        @Test
        @DisplayName("an out-of-range HH:MM does not even tokenize (syntax error)")
        void outOfRangeTime_isSyntaxError() {
            List<String> errors = model.setProgram("at 25:00 {\n  semaphore 1 open;\n}");

            assertFalse(errors.isEmpty(), "25:00 is not a TIME token");
            assertTrue(service().triggers().isEmpty());
        }

        @Test
        @DisplayName("an exact duplicate is warned and registered once")
        void duplicate_isWarnedAndRegisteredOnce() {
            List<String> errors = model.setProgram("""
                    at 6:30 {
                        semaphore 1 open;
                    }
                    at 6:30 {
                        semaphore 1 close;
                    }
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 30))), service().triggers());
            assertTrue(
                    notices.stream()
                            .anyMatch(n -> n.contains("Duplicate temporal trigger 'at 06:30'")),
                    notices.toString());
        }

        @Test
        @DisplayName("the active limit is enforced with a visible warning")
        void limit_isWarnedAndKeepsTheFirstTriggers() {
            StringBuilder program = new StringBuilder();
            for (int i = 0; i <= TemporalTriggerService.MAX_TRIGGERS; i++) {
                program.append("at ").append(i / 60).append(':')
                        .append(String.format("%02d", i % 60))
                        .append(" {\n  semaphore 1 open;\n}\n");
            }

            List<String> errors = model.setProgram(program.toString());

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals(TemporalTriggerService.MAX_TRIGGERS, service().triggers().size());
            assertTrue(notices.stream().anyMatch(n -> n.contains("Too many temporal triggers")),
                    notices.toString());
        }
    }

    @Nested
    @DisplayName("Program replace re-registers the triggers")
    class ReRegistration {

        @Test
        @DisplayName("the new program's triggers replace the old ones")
        void replacingTheProgram_dropsTheOldTriggers() {
            model.setProgram("at 6:30 {\n  semaphore 1 open;\n}");
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 30))), service().triggers());

            model.setProgram("every 1h {\n  semaphore 1 open;\n}");

            assertEquals(List.of(TemporalTrigger.every(60)), service().triggers());
            assertEquals(-1, service().ticksUntilNextFire(TemporalTrigger.at(LocalTime.of(6, 30))));
        }

        @Test
        @DisplayName("a broken program leaves the applied triggers in place")
        void brokenProgram_keepsTheAppliedTriggers() {
            model.setProgram("at 6:30 {\n  semaphore 1 open;\n}");

            List<String> errors = model.setProgram("every 1h {\n  semaphore 1 open;");

            assertFalse(errors.isEmpty(), "the broken program must be rejected");
            assertEquals(List.of(TemporalTrigger.at(LocalTime.of(6, 30))), service().triggers());
        }

        @Test
        @DisplayName("an empty program clears the triggers")
        void emptyProgram_clearsTheTriggers() {
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");

            List<String> errors = model.setProgram("");

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(service().triggers().isEmpty());
        }
    }

    @Nested
    @DisplayName("Scheduling from the game clock")
    class Scheduling {

        @Test
        @DisplayName("every 30m registered at 10:07 arms 10:30 and re-arms 11:00 on the grid")
        void every30m_armsAndReArmsOnTheGrid() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");
            TemporalTrigger trigger = TemporalTrigger.every(30);

            assertEquals(460, service().ticksUntilNextFire(trigger));

            // The scheduler ticks before the clock: the armed fire lands when the clock still shows
            // 10:29 and the service re-arms the same target; the clock reaches 10:30 right after.
            runTicks(460);
            assertEquals(new GameTime(1, 10, 30), model.getGameClock().now());
            assertEquals(1, service().ticksUntilNextFire(trigger));

            runTicks(1);
            assertEquals(new GameTime(1, 10, 30), model.getGameClock().now());
            assertEquals(600, service().ticksUntilNextFire(trigger),
                    "the next grid point is 11:00, 30 game minutes away");
        }

        @Test
        @DisplayName("every 30m from 06:15 arms the anchored grid (10:07 -> 10:15)")
        void everyFrom_armsTheAnchoredGrid() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));
            model.setProgram("every 30m from 6:15 {\n  semaphore 1 open;\n}");

            assertEquals(160,
                    service().ticksUntilNextFire(TemporalTrigger.every(30, LocalTime.of(6, 15))));
        }

        @Test
        @DisplayName("at 00:10 registered at 23:50 rolls over midnight and re-arms daily")
        void atTrigger_rollsOverMidnight() {
            model.getGameClock().setTime(new GameTime(1, 23, 50));
            model.setProgram("at 0:10 {\n  semaphore 1 open;\n}");
            TemporalTrigger trigger = TemporalTrigger.at(LocalTime.of(0, 10));

            assertEquals(400, service().ticksUntilNextFire(trigger));

            runTicks(401);

            assertEquals(new GameTime(2, 0, 10), model.getGameClock().now());
            assertEquals(28800, service().ticksUntilNextFire(trigger),
                    "the next daily fire is 24 game hours away");
        }
    }
}
