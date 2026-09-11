package letrain.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for merging recorded commands (ADR-020). The command journal and the undo history must stay
 * in lockstep, so both call these predicates before appending a new canonical command.
 */
final class CommandMerge {

    // "set" statements are absolute (last value wins), so consecutive ones for the same element and
    // property collapse. Toggles (invert/flip) are NOT merged here: dropping one would change parity.

    /** {@code signal N set limit X;}, allowing the {@code go ...; face ...; } prefix. */
    private static final Pattern SIGNAL_LIMIT = Pattern.compile("signal (\\d+) set limit \\d+;");

    /** {@code signal N set mode max|min;}. */
    private static final Pattern SIGNAL_MODE = Pattern.compile("signal (\\d+) set mode (?:max|min);");

    /** {@code fork N set <route>;}. */
    private static final Pattern FORK_ROUTE = Pattern.compile("fork (\\d+) set [a-z]+;");

    private CommandMerge() {}

    /**
     * True when {@code next} sets the same property of the same element as {@code previous}, so the
     * two collapse into a single entry (the last value wins). This is what makes dragging a signal
     * limit, or flipping a fork back and forth, leave one command instead of one per keypress.
     */
    static boolean consecutiveProperty(String previous, String next) {
        String key = propertyKey(previous);
        return key != null && key.equals(propertyKey(next));
    }

    /** {@code kind:id:property}, or null when the command is not an absolute state set. */
    private static String propertyKey(String command) {
        if (command == null) {
            return null;
        }
        Matcher m = SIGNAL_LIMIT.matcher(command);
        if (m.find()) {
            return "signal:" + m.group(1) + ":limit";
        }
        m = SIGNAL_MODE.matcher(command);
        if (m.find()) {
            return "signal:" + m.group(1) + ":mode";
        }
        m = FORK_ROUTE.matcher(command);
        if (m.find()) {
            return "fork:" + m.group(1) + ":route";
        }
        return null;
    }
}
