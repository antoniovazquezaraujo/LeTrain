package letrain.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for merging recorded commands (ADR-020). The command journal and the undo history must stay
 * in lockstep, so both call these predicates before appending a new canonical command.
 */
final class CommandMerge {

    /** A {@code signal N set limit X;} statement, allowing the {@code go ...; face ...; } prefix. */
    private static final Pattern SIGNAL_LIMIT = Pattern.compile("signal (\\d+) set limit \\d+;");

    private CommandMerge() {}

    /**
     * True when {@code next} is a "set limit" tweak for the same signal as {@code previous}, so the
     * two collapse into a single entry (the last value wins). This is what makes dragging a signal
     * limit up/down leave one command instead of one per keypress.
     */
    static boolean consecutiveSignalLimit(String previous, String next) {
        if (previous == null || next == null) {
            return false;
        }
        Matcher p = SIGNAL_LIMIT.matcher(previous);
        Matcher n = SIGNAL_LIMIT.matcher(next);
        return p.find() && n.find() && p.group(1).equals(n.group(1));
    }
}
