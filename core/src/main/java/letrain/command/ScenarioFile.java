package letrain.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scenario file format (ADR-020 roadmap, item 4). A scenario is a plain-text file that rebuilds a
 * world's infrastructure (and operator) on top of a terrain seed:
 *
 * <pre>
 * # LeTrain scenario v1
 * seed 123
 * on build {
 *   go 0,0; face e; write 5;
 *   new st;
 * }
 * on start {
 *   semaphore 1 close;
 * }
 * </pre>
 *
 * <p>
 * The {@code on build} section holds the canonical editing commands (the command journal) and is
 * replayed first; {@code on start} holds optional initial conditions applied right after the build.
 * Both sections are optional: a flat file with just a seed and commands (no braces) is valid and its
 * lines are treated as build commands, so older scenarios keep working.
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

    /** Parsed scenario: seed + the two ordered command sections. */
    public record Scenario(int seed, List<String> buildCommands, List<String> startCommands) {}

    private ScenarioFile() {}

    /** True when {@code name} looks like a scenario file ({@code *.ltr}). */
    public static boolean isScenarioName(String name) {
        return name != null && name.toLowerCase().endsWith(EXTENSION);
    }

    /** Renders the seed and the build commands (no {@code on start}) as scenario text. */
    public static String render(int seed, List<String> buildCommands) {
        return render(seed, buildCommands, null);
    }

    /** Renders {@code seed} + the {@code on build}/{@code on start} sections as scenario text. */
    public static String render(int seed, List<String> buildCommands, List<String> startCommands) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append(SEED_PREFIX).append(seed).append('\n');
        sb.append("on build {\n");
        for (String command : optimize(buildCommands)) {
            sb.append(command).append('\n');
        }
        sb.append("}\n");
        if (startCommands != null && !startCommands.isEmpty()) {
            sb.append("on start {\n");
            for (String command : startCommands) {
                if (command != null && !command.trim().isEmpty()) {
                    sb.append(command.trim()).append('\n');
                }
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /**
     * Parses the terrain seed and the two command sections. Flat files without sections put all
     * commands in {@code on build}.
     *
     * @throws IllegalArgumentException when the file has no {@code seed} line
     */
    public static Scenario parse(String text) {
        int seed = parseSeed(text);
        List<String> build = new ArrayList<>();
        List<String> start = new ArrayList<>();
        boolean inStart = false;
        for (String rawLine : safeLines(text)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(SEED_PREFIX)) {
                continue;
            }
            String lower = line.toLowerCase();
            if (lower.startsWith("on build")) {
                inStart = false;
                continue;
            }
            if (lower.startsWith("on start")) {
                inStart = true;
                continue;
            }
            if (line.equals("}")) {
                inStart = false;
                continue;
            }
            (inStart ? start : build).add(line);
        }
        return new Scenario(seed, build, start);
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

    private static String[] safeLines(String text) {
        return text == null ? new String[0] : text.split("\\R");
    }
}
