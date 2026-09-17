package letrain.soundscape.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import letrain.soundscape.ClimatePreset;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.StyleLoader;
import letrain.soundscape.audio.SampleResolver;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;

/**
 * Test player without LeTrain: loads a style, sets an ambient state and prints the resulting mix.
 *
 * <pre>
 * soundscape --time 23:30 --zones "sea=0.5,fields=0.7" --height 0.2 --weather drizzle
 * soundscape --day --zones "gold-mine=0.7,fields=0.4"
 * </pre>
 */
public final class SoundscapeCli {

    private static final String DEFAULT_STYLE = "/styles/valle-norte.sound";
    private static final float PRINT_THRESHOLD = 0.005f;
    private static final float DAY_THRESHOLD = 0.02f;

    private final StyleLoader loader;
    private final SoundscapeEngine engine;

    public SoundscapeCli() {
        this(new TextStyleLoader(), new SoundscapeEngineImpl());
    }

    SoundscapeCli(StyleLoader loader, SoundscapeEngine engine) {
        this.loader = loader;
        this.engine = engine;
    }

    public static void main(String[] args) {
        int code = new SoundscapeCli().run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        String file = null;
        LocalTime time = LocalTime.of(12, 0);
        Map<String, Float> zones = new LinkedHashMap<>();
        float height = 0f;
        String weatherPreset = null;
        Float rain = null;
        Float wind = null;
        Float storm = null;
        boolean day = false;
        boolean checkAssets = false;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help", "-h" -> {
                        printUsage(out);
                        return 0;
                    }
                    case "--file" -> file = required(args, ++i, arg);
                    case "--time" -> time = LocalTime.parse(required(args, ++i, arg));
                    case "--zones" -> zones = parseZones(required(args, ++i, arg));
                    case "--height" -> height = Float.parseFloat(required(args, ++i, arg));
                    case "--weather" -> weatherPreset = required(args, ++i, arg);
                    case "--rain" -> rain = Float.parseFloat(required(args, ++i, arg));
                    case "--wind" -> wind = Float.parseFloat(required(args, ++i, arg));
                    case "--storm" -> storm = Float.parseFloat(required(args, ++i, arg));
                    case "--day" -> day = true;
                    case "--check-assets" -> checkAssets = true;
                    default -> throw new IllegalArgumentException("unknown option: " + arg);
                }
            }
        } catch (IllegalArgumentException | DateTimeParseException
                | ArrayIndexOutOfBoundsException e) {
            err.println("error: " + e.getMessage());
            err.println("try --help");
            return 2;
        }

        SoundscapeStyle style;
        try {
            style = file != null ? loader.load(Path.of(file)) : loader.loadResource(DEFAULT_STYLE);
        } catch (IOException e) {
            err.println("error: could not load style: " + e.getMessage());
            return 2;
        }

        ClimatePreset preset = resolvePreset(style, weatherPreset, err);
        if (preset == null && weatherPreset != null) {
            return 2;
        }
        float rainIntensity = rain != null ? rain : preset != null ? preset.rain() : 0f;
        float windIntensity = wind != null ? wind : preset != null ? preset.wind() : 0f;
        float stormIntensity = storm != null ? storm : preset != null ? preset.storm() : 0f;

        String styleName =
                file != null ? Path.of(file).getFileName().toString() : "valle-norte.sound";
        if (checkAssets) {
            return checkAssets(style, styleName, out);
        }
        out.println(header(styleName, time, zones, height, weatherPreset, rainIntensity,
                windIntensity, stormIntensity));

        if (day) {
            printDay(out, style, zones, height, rainIntensity, windIntensity, stormIntensity);
        } else {
            Composition composition = engine.compose(style, new CompositionInput(time, zones,
                    height, rainIntensity, windIntensity, stormIntensity));
            printComposition(out, composition);
        }
        return 0;
    }

    private ClimatePreset resolvePreset(SoundscapeStyle style, String name, PrintStream err) {
        if (name == null) {
            return null;
        }
        ClimatePreset preset = style.climatePresets().get(name.toLowerCase());
        if (preset == null) {
            err.println("error: unknown weather preset '" + name + "'; available: "
                    + style.climatePresets().keySet());
        }
        return preset;
    }

    private int checkAssets(SoundscapeStyle style, String styleName, PrintStream out) {
        SampleResolver resolver = new SampleResolver();
        out.println("assets for " + styleName + ":");
        int missing = 0;
        for (Map.Entry<String, letrain.soundscape.SoundDef> entry : style.sounds().entrySet()) {
            missing += printAsset(out, resolver, entry.getKey(), entry.getValue().material());
        }
        for (Map.Entry<String, letrain.soundscape.SoundDef> entry : style.weatherSounds()
                .entrySet()) {
            missing += printAsset(out, resolver, "weather-" + entry.getKey(),
                    entry.getValue().material());
        }
        out.println(missing == 0 ? "all assets resolved" : missing + " sound(s) without assets");
        return missing == 0 ? 0 : 3;
    }

    private int printAsset(PrintStream out, SampleResolver resolver, String name,
            List<String> materials) {
        List<URL> urls = new ArrayList<>();
        for (String material : materials) {
            urls.addAll(resolver.resolve(material));
        }
        if (urls.isEmpty()) {
            out.printf("  %-18s MISSING (%s)%n", name, materials);
            return 1;
        }
        StringBuilder files = new StringBuilder();
        for (URL url : urls) {
            if (files.length() > 0) {
                files.append(", ");
            }
            files.append(fileName(url));
        }
        out.printf("  %-18s %d file(s): %s%n", name, urls.size(), files);
        return 0;
    }

    private String fileName(URL url) {
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void printDay(PrintStream out, SoundscapeStyle style, Map<String, Float> zones,
            float height, float rain, float wind, float storm) {
        out.println();
        for (int minute = 0; minute < 24 * 60; minute += 30) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            Composition composition = engine.compose(style,
                    new CompositionInput(time, zones, height, rain, wind, storm));
            out.println(String.format(Locale.ROOT, "%s  %s", formatTime(time),
                    formatActive(composition)));
        }
    }

    private void printComposition(PrintStream out, Composition composition) {
        out.println();
        List<Map.Entry<String, Float>> audible = composition.volumes().entrySet().stream()
                .filter(entry -> entry.getValue() > PRINT_THRESHOLD)
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        if (audible.isEmpty()) {
            out.println("  (silence: set zones with --zones and weather with --weather)");
            return;
        }
        audible.forEach(entry -> out.println(
                String.format(Locale.ROOT, "  %.2f  %s", entry.getValue(), entry.getKey())));
    }

    private String formatActive(Composition composition) {
        List<Map.Entry<String, Float>> active = new ArrayList<>(composition.volumes().entrySet());
        active.removeIf(entry -> entry.getValue() <= DAY_THRESHOLD);
        active.sort(Map.Entry.<String, Float>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        if (active.isEmpty()) {
            return "silence";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Float> entry : active) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(entry.getKey()).append(' ')
                    .append(String.format(Locale.ROOT, "%.2f", entry.getValue()));
        }
        return sb.toString();
    }

    private String header(String styleName, LocalTime time, Map<String, Float> zones, float height,
            String weatherPreset, float rain, float wind, float storm) {
        StringBuilder zoneList = new StringBuilder();
        zones.forEach((zone, weight) -> {
            if (zoneList.length() > 0) {
                zoneList.append(' ');
            }
            zoneList.append(String.format(Locale.ROOT, "%s %.2f", zone, weight));
        });
        String weather = weatherPreset != null ? weatherPreset : "custom";
        return String.format(Locale.ROOT,
                "style: %s · %s · height %.2f · weather %s (rain %.2f wind %.2f storm %.2f)%n"
                        + "zones: %s",
                styleName, formatTime(time), height, weather, rain, wind, storm,
                zoneList.length() > 0 ? zoneList : "(none)");
    }

    private String formatTime(LocalTime time) {
        return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }

    private Map<String, Float> parseZones(String value) {
        Map<String, Float> zones = new LinkedHashMap<>();
        if (value.isBlank()) {
            return zones;
        }
        for (String pair : value.split(",")) {
            String[] parts = pair.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "invalid zone weight: " + pair + " (expected zone=0.5)");
            }
            zones.put(parts[0].trim().toLowerCase(), Float.parseFloat(parts[1].trim()));
        }
        return zones;
    }

    private String required(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private void printUsage(PrintStream out) {
        out.println("""
                Soundscape test player

                Usage: soundscape [options]

                  --file <path>       style file (default: bundled styles/valle-norte.sound)
                  --time <HH:mm>      exact time of day (default 12:00)
                  --zones <list>      zone weights, e.g. "sea=0.5,fields=0.7"
                  --height <0..1>     listening height/zoom (default 0)
                  --weather <name>    climate preset from the style (e.g. drizzle)
                  --rain <0..1>       rain intensity override
                  --wind <0..1>       wind intensity override
                  --storm <0..1>      storm intensity override
                  --day               print a full-day timeline every 30 game minutes
                  --check-assets      resolve style materials and report missing files
                  --help              this help""");
    }
}
