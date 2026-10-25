package letrain.mvp.impl.terminal;

import java.util.Locale;
import java.util.Set;

/**
 * Console whitelist while a route preview session is active (ADR-036).
 *
 * <p>
 * In {@code CHOOSING} the author may open the console with {@code :}, but only read-only or
 * navigation commands are allowed: the route, its endpoints and the world must stay untouched until
 * the plan is selected or discarded. The check is deliberately syntactic: it looks at the command
 * name of every semicolon-separated statement and rejects the whole line if any of them is not
 * whitelisted.
 */
final class RoutePreviewConsolePolicy {

    /** Error shown in the command bar when a blocked command is attempted. */
    static final String BLOCKED_MESSAGE = "Only navigation commands are available while previewing";

    /**
     * Allowed command names: aliases included ({@code g} for {@code go}, {@code m} for
     * {@code mark}).
     */
    private static final Set<String> ALLOWED = Set.of("go", "g", "face", "mark", "m", "info", "ls");

    private RoutePreviewConsolePolicy() {}

    /**
     * True when every non-empty semicolon-separated statement starts with an allowed command name.
     * Case and surrounding whitespace are ignored; an all-blank line is trivially allowed.
     */
    static boolean isAllowed(String commandLine) {
        if (commandLine == null) {
            return true;
        }
        for (String statement : commandLine.split(";")) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String name = firstWord(trimmed).toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private static String firstWord(String statement) {
        int end = 0;
        while (end < statement.length() && !Character.isWhitespace(statement.charAt(end))) {
            end++;
        }
        return statement.substring(0, end);
    }
}
