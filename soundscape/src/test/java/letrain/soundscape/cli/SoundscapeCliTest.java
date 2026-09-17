package letrain.soundscape.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape test player CLI")
class SoundscapeCliTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final SoundscapeCli cli = new SoundscapeCli();

    private int run(String... args) {
        return cli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String errors() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("prints the mix for the given state")
    void should_PrintMix_When_StateIsGiven() {
        int code = run("--time", "23:30", "--zones", "sea=0.5,fields=0.7", "--height", "0.2",
                "--weather", "drizzle");

        assertEquals(0, code);
        assertTrue(output().contains("crickets"));
        assertTrue(output().contains("0.36"), () -> output());
        assertTrue(output().contains("weather-rain"));
        assertTrue(output().contains("0.30"));
    }

    @Test
    @DisplayName("prints a full-day timeline with --day")
    void should_PrintTimeline_When_DayIsRequested() {
        int code = run("--day", "--zones", "fields=0.7", "--weather", "clear");

        assertEquals(0, code);
        assertTrue(output().contains("00:00"));
        assertTrue(output().contains("12:00"));
    }

    @Test
    @DisplayName("fails with a clear message on an unknown weather preset")
    void should_Fail_When_WeatherPresetIsUnknown() {
        int code = run("--weather", "hurricane");

        assertEquals(2, code);
        assertTrue(errors().contains("unknown weather preset"));
    }

    @Test
    @DisplayName("explains silence when no zones are given")
    void should_ExplainSilence_When_NoZones() {
        int code = run();

        assertEquals(0, code);
        assertTrue(output().contains("silence"));
    }

    @Test
    @DisplayName("prints usage with --help")
    void should_PrintUsage_When_HelpRequested() {
        assertEquals(0, run("--help"));
        assertTrue(output().contains("Usage: soundscape"));
    }
}
