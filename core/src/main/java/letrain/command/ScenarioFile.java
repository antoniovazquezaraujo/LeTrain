package letrain.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Scenario file format (ADR-020 roadmap, item 4 — phase 1). A scenario is a small text file that
 * rebuilds a world's infrastructure on top of a given terrain seed: a {@code seed} header plus the
 * canonical editing commands (the command journal) one per line.
 *
 * <pre>
 * # LeTrain scenario v1
 * seed 123
 * go 0,0; face e; write 5;
 * new st;
 * </pre>
 *
 * <p>
 * This class only parses/renders the text; it never touches a model or the filesystem. The caller
 * reads/writes the file and replays {@link #commandLines(String)} through its own command machinery
 * (the same one the console and undo use), on a fresh {@code Model(seed)}.
 *
 * <p>
 * Blank lines and {@code #} comments are ignored, so users can annotate scenarios by hand (external
 * editor friendly). The format leaves room to grow into explicit {@code on build}/{@code on start}
 * sections later without breaking these files.
 */
public final class ScenarioFile {

    public static final String EXTENSION = ".ltr";
    private static final String HEADER = "# LeTrain scenario v1";
    private static final String SEED_PREFIX = "seed ";

    private ScenarioFile() {}

    /** True when {@code name} looks like a scenario file ({@code *.ltr}). */
    public static boolean isScenarioName(String name) {
        return name != null && name.toLowerCase().endsWith(EXTENSION);
    }

    /** Renders {@code seed} + the given commands as scenario text. */
    public static String render(int seed, List<String> commands) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append(SEED_PREFIX).append(seed).append('\n');
        if (commands != null) {
            for (String command : commands) {
                if (command != null && !command.trim().isEmpty()) {
                    sb.append(command.trim()).append('\n');
                }
            }
        }
        return sb.toString();
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

    /** The replayable commands, skipping blank lines, comments and the seed header. */
    public static List<String> commandLines(String text) {
        List<String> commands = new ArrayList<>();
        for (String rawLine : safeLines(text)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(SEED_PREFIX)) {
                continue;
            }
            commands.add(line);
        }
        return commands;
    }

    private static String[] safeLines(String text) {
        return text == null ? new String[0] : text.split("\\R");
    }
}
