package letrain.command;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scenario file format (ADR-020 roadmap, item 4). A scenario is a plain-text file that rebuilds a
 * world's infrastructure (and operator) on top of a terrain seed:
 *
 * <pre>
 * # LeTrain scenario v1
 * seed 123
 * configuration {
 *   threshold.WATER=130
 * }
 * on build {
 *   go 0,0; face e; write 5;
 *   new st;
 * }
 * on start {
 *   semaphore 1 close;
 * }
 * program {
 *   sensor 1 on train enter { semaphore 1 open; }
 * }
 * </pre>
 *
 * <p>
 * {@code configuration} holds the game settings (the effective {@code letrain.cfg}) so the scenario
 * reproduces the same terrain and rules; it wins over the local file. {@code on build} holds the
 * canonical editing commands (the command journal) and is replayed first; {@code on start} holds
 * optional initial conditions applied right after the build; {@code program} holds the
 * automation script installed after that (may nest braces). All sections are optional: a flat file
 * with just a seed and commands (no braces) is valid and its lines are treated as build commands, so
 * older scenarios keep working.
 *
 * <p>
 * This class only parses/renders the text; it never touches a model or the filesystem. The caller
 * reads/writes the file and replays the commands through its own command machinery (the same one the
 * console and undo use), on a fresh {@code Model(seed)}. Blank lines and {@code #} comments are
 * ignored, so users can annotate scenarios by hand (external editor friendly).
 */
public final class ScenarioFile {

    public static final String EXTENSION = ".ltr";
    private static final String HEADER = "# LeTrain scenario v1";
    private static final String SEED_PREFIX = "seed ";

    /** A canonical, pure straight build: {@code go x,y; face d; write n;} (no turns/moves). */
    private static final Pattern STRAIGHT_WRITE =
            Pattern.compile("go (-?\\d+),(-?\\d+); face ([a-z]{1,2}); write (\\d+);");

    /**
     * Parsed scenario: seed + the game settings + the ordered build/start command sections + the
     * automation program (stored at base column 0), which may be empty.
     */
    public record Scenario(int seed, Map<String, String> configuration,
            List<String> buildCommands, List<String> startCommands, String program) {}

    private ScenarioFile() {}

    /** True when {@code name} looks like a scenario file ({@code *.ltr}). */
    public static boolean isScenarioName(String name) {
        return name != null && name.toLowerCase().endsWith(EXTENSION);
    }

    /** Renders the seed and the build commands (no {@code on start}) as scenario text. */
    public static String render(int seed, List<String> buildCommands) {
        return render(seed, null, buildCommands, null, null);
    }

    /** Renders {@code seed} + the {@code on build}/{@code on start} sections as scenario text. */
    public static String render(int seed, List<String> buildCommands, List<String> startCommands) {
        return render(seed, null, buildCommands, startCommands, null);
    }

    /**
     * Renders {@code seed} + the {@code on build}/{@code on start}/{@code program} sections. The
     * program is re-indented by brace depth (comments preserved), so nested blocks line up.
     */
    public static String render(int seed, List<String> buildCommands, List<String> startCommands,
            String program) {
        return render(seed, null, buildCommands, startCommands, program);
    }

    /**
     * Renders the full scenario. {@code configuration} is the game settings map (written sorted as
     * {@code key=value}); {@code program} is re-indented by brace depth so nested blocks line up.
     */
    public static String render(int seed, Map<String, String> configuration,
            List<String> buildCommands, List<String> startCommands, String program) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append(SEED_PREFIX).append(seed).append('\n');
        sb.append(configurationSection(configuration));
        sb.append("on build {\n");
        sb.append(indentBody(String.join("\n", optimize(buildCommands)), 1));
        sb.append("}\n");
        if (startCommands != null && !startCommands.isEmpty()) {
            sb.append("on start {\n");
            sb.append(indentBody(String.join("\n", startCommands), 1));
            sb.append("}\n");
        }
        if (program != null && !program.isBlank()) {
            sb.append(programSection(program));
        }
        return sb.toString();
    }

    /**
     * Parses the terrain seed and the {@code on build}/{@code on start}/{@code program} sections.
     * Flat files without sections put all commands in {@code on build}. Section bodies are read with
     * a brace-depth counter, so the {@code program} section may contain nested blocks
     * ({@code trigger ... { ... }}); the program keeps its comments and relative indentation (the
     * wrapper's base indent is stripped) so it stays editable by hand.
     *
     * @throws IllegalArgumentException when the file has no {@code seed} line
     */
    public static Scenario parse(String text) {
        int seed = parseSeed(text);
        List<String> configLines = new ArrayList<>();
        List<String> build = new ArrayList<>();
        List<String> start = new ArrayList<>();
        StringBuilder program = new StringBuilder();
        List<String> target = null; // non-null while inside a build/start section
        boolean inProgram = false;
        boolean inConfig = false;
        int depth = 0;
        for (String rawLine : safeLines(text)) {
            String line = rawLine.trim();
            if (depth == 0) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(SEED_PREFIX)) {
                    continue;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("configuration")) {
                    inConfig = true;
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("on build")) {
                    target = build;
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("on start")) {
                    target = start;
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("program")) {
                    inProgram = true;
                    depth = 1;
                    continue;
                }
                build.add(line); // legacy flat file: everything is a build command
                continue;
            }
            if (inConfig) {
                int delta = braceDelta(line);
                if (depth + delta > 0 && !line.isEmpty()) {
                    configLines.add(rawLine);
                }
                depth += delta;
                if (depth <= 0) {
                    depth = 0;
                    inConfig = false;
                }
            } else if (inProgram) {
                int delta = braceDelta(line);
                if (depth + delta > 0) {
                    if (program.length() > 0) {
                        program.append('\n');
                    }
                    program.append(rawLine); // verbatim, indentation preserved
                }
                depth += delta;
                if (depth <= 0) {
                    depth = 0;
                    inProgram = false;
                }
            } else {
                if (line.startsWith("#")) {
                    continue; // comments are ignored inside build/start
                }
                int delta = braceDelta(line);
                if (depth + delta > 0 && target != null) {
                    target.add(line);
                }
                depth += delta;
                if (depth <= 0) {
                    depth = 0;
                    target = null;
                }
            }
        }
        return new Scenario(seed, parseConfig(configLines), build, start,
                stripCommonIndent(program.toString().stripTrailing()));
    }

    /**
     * Removes the common leading indentation shared by all non-blank lines, preserving relative
     * nesting. The in-memory program is thus stored at base column 0; the renderer re-indents it
     * inside the {@code program { ... }} wrapper.
     */
    private static String stripCommonIndent(String text) {
        String[] lines = safeLines(text);
        int min = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int indent = 0;
            while (indent < line.length()
                    && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
                indent++;
            }
            min = Math.min(min, indent);
        }
        if (min == Integer.MAX_VALUE || min == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.length() >= min ? line.substring(min) : line).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Parses {@code key=value} settings lines (java.util.Properties syntax) into an ordered map. */
    private static Map<String, String> parseConfig(List<String> lines) {
        Map<String, String> config = new LinkedHashMap<>();
        if (lines == null || lines.isEmpty()) {
            return config;
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(String.join("\n", lines)));
        } catch (Exception e) {
            // Ignore malformed lines; whatever parsed is kept.
        }
        for (String name : props.stringPropertyNames()) {
            config.put(name, props.getProperty(name));
        }
        return config;
    }

    /** Net brace count of a line ({@code {}), used to follow nested program blocks. */
    private static int braceDelta(String line) {
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '{') {
                delta++;
            } else if (line.charAt(i) == '}') {
                delta--;
            }
        }
        return delta;
    }

    /**
     * Re-indents a section body with two spaces per brace nesting level, starting at {@code base}
     * (1 for a top-level section, so its contents sit two spaces in). Original leading whitespace is
     * discarded and recomputed from the braces, so nested blocks line up. Comments and inline
     * {@code { ... }} blocks are preserved.
     */
    private static String indentBody(String body, int base) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (String raw : safeLines(body)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                sb.append('\n');
                continue;
            }
            int indent = Math.max(0, depth - leadingCloses(line));
            sb.append("  ".repeat(base + indent)).append(line).append('\n');
            depth = Math.max(0, depth + braceDelta(line));
        }
        return sb.toString();
    }

    /** Number of leading {@code }} before any other character (a line that closes one or more blocks). */
    private static int leadingCloses(String line) {
        int closes = 0;
        while (closes < line.length() && line.charAt(closes) == '}') {
            closes++;
        }
        return closes;
    }

    /**
     * Compacts the command list for a scenario (readability, per ADR-020): consecutive pure straight
     * builds in the same direction are merged into a single {@code write N}. This only touches the
     * exported text; the undo history keeps its per-tile granularity. Any non-straight command, a
     * turn, or a non-contiguous start breaks the run, so the replayed result is unchanged.
     */
    public static List<String> optimize(List<String> commands) {
        List<String> out = new ArrayList<>();
        String runDir = null;
        int runX = 0;
        int runY = 0;
        int runCount = 0;
        int nextX = 0;
        int nextY = 0;

        if (commands != null) {
            for (String raw : commands) {
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                String command = raw.trim();
                Matcher m = STRAIGHT_WRITE.matcher(command);
                if (m.matches()) {
                    int x = Integer.parseInt(m.group(1));
                    int y = Integer.parseInt(m.group(2));
                    String dir = m.group(3);
                    int count = Integer.parseInt(m.group(4));
                    if (runDir != null && runDir.equals(dir) && x == nextX && y == nextY) {
                        runCount += count;
                    } else {
                        flushRun(out, runX, runY, runDir, runCount);
                        runDir = dir;
                        runX = x;
                        runY = y;
                        runCount = count;
                    }
                    int[] v = vector(runDir);
                    nextX = x + v[0] * count;
                    nextY = y + v[1] * count;
                } else {
                    flushRun(out, runX, runY, runDir, runCount);
                    runDir = null;
                    runCount = 0;
                    out.add(command);
                }
            }
        }
        flushRun(out, runX, runY, runDir, runCount);
        return out;
    }

    private static void flushRun(List<String> out, int x, int y, String dir, int count) {
        if (dir != null && count > 0) {
            out.add("go " + x + "," + y + "; face " + dir + "; write " + count + ";");
        }
    }

    /** Direction unit vector, matching {@code Point.move(Dir, distance)}. */
    private static int[] vector(String dir) {
        switch (dir) {
            case "n":
                return new int[] {0, -1};
            case "ne":
                return new int[] {1, -1};
            case "e":
                return new int[] {1, 0};
            case "se":
                return new int[] {1, 1};
            case "s":
                return new int[] {0, 1};
            case "sw":
                return new int[] {-1, 1};
            case "w":
                return new int[] {-1, 0};
            case "nw":
                return new int[] {-1, -1};
            default:
                return new int[] {0, 0};
        }
    }

    /**
     * Parses the terrain seed from the scenario header.
     *
     * @throws IllegalArgumentException when the file has no {@code seed} line
     */
    public static int parseSeed(String text) {
        for (String rawLine : safeLines(text)) {
            String line = rawLine.trim();
            if (line.startsWith(SEED_PREFIX)) {
                try {
                    return Integer.parseInt(line.substring(SEED_PREFIX.length()).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid scenario seed: '" + line + "'", e);
                }
            }
        }
        throw new IllegalArgumentException("Scenario has no '" + SEED_PREFIX.trim() + "' line");
    }

    /** The build commands (backward-compatible shortcut for {@code parse(text).buildCommands()}). */
    public static List<String> commandLines(String text) {
        return parse(text).buildCommands();
    }

    /** Wraps raw program text in a {@code program { ... }} section, indented by brace depth. */
    public static String programSection(String program) {
        StringBuilder sb = new StringBuilder("program {\n");
        if (program != null && !program.isBlank()) {
            sb.append(indentBody(program, 1));
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Extracts the body of a {@code program { ... }} section at base column 0. If {@code section}
     * has no program wrapper it is returned as-is (so the editor accepts raw program text too).
     */
    public static String programSectionBody(String section) {
        if (section == null || section.isBlank()) {
            return "";
        }
        String trimmed = section.trim();
        if (!trimmed.toLowerCase().startsWith("program")) {
            return trimmed; // raw program text without the wrapper
        }
        try {
            return parse(SEED_PREFIX + "0\n" + section).program();
        } catch (Exception e) {
            return trimmed;
        }
    }

    /**
     * Renders a {@code configuration { ... }} section from a settings map (written sorted as
     * {@code key=value}). Returns an empty string when there is nothing to write.
     */
    public static String configurationSection(Map<String, String> configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        configuration.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> body.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
        return "configuration {\n" + indentBody(body.toString(), 1) + "}\n";
    }

    /**
     * Parses the body of a {@code configuration { ... }} section into an ordered settings map. If
     * {@code section} has no wrapper it is parsed as raw {@code key=value} lines (so the editor
     * accepts bare settings too). Malformed lines are ignored.
     */
    public static Map<String, String> configurationSectionBody(String section) {
        if (section == null || section.isBlank()) {
            return new LinkedHashMap<>();
        }
        String trimmed = section.trim();
        if (!trimmed.toLowerCase().startsWith("configuration")) {
            return parseConfig(new ArrayList<>(List.of(section.split("\\R"))));
        }
        try {
            return parse(SEED_PREFIX + "0\n" + section).configuration();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * A scenario split into its three editor parts: the world recipe (seed + on build + on start,
     * no settings or program), the {@code configuration { ... }} section and the
     * {@code program { ... }} section. Either section may be empty.
     */
    public record Parts(int seed, String scenarioText, String configurationText, String programText) {}

    /** Splits a full scenario text into the three editor parts (see {@link Parts}). */
    public static Parts split(String fullText) {
        Scenario s = parse(fullText);
        return new Parts(s.seed(),
                render(s.seed(), null, s.buildCommands(), s.startCommands(), null),
                configurationSection(s.configuration()),
                programSection(s.program()));
    }

    /**
     * Builds a full scenario text from the three editor parts. Sections are re-rendered in canonical
     * order (seed + settings + on build + on start + program), so callers can concatenate the parts
     * in any order.
     */
    public static String compose(String scenarioText, String configurationText, String programText) {
        Scenario base = parse(scenarioText);
        return render(base.seed(), configurationSectionBody(configurationText),
                base.buildCommands(), base.startCommands(), programSectionBody(programText));
    }

    private static String[] safeLines(String text) {
        return text == null ? new String[0] : text.split("\\R");
    }
}
