package letrain.command;

/**
 * Trims raw ANTLR diagnostics so they fit command-line and overlay output. The long
 * {@code expecting { ... }} token set is dropped (the position already points at the problem) and
 * very long messages are capped.
 *
 * <p>
 * Shared by the scenario validator ({@link ScenarioCompiler}) and the console
 * ({@code PlayerCommandExecutor}).
 */
public final class SyntaxMessages {

    /** Longest text that fits the one-line command bar. */
    private static final int MAX_LENGTH = 60;

    private SyntaxMessages() {}

    /** A short, single-line description of a parser message. */
    public static String shorten(String msg) {
        if (msg == null) {
            return "syntax error";
        }
        int expected = msg.indexOf(" expecting ");
        if (expected >= 0) {
            msg = msg.substring(0, expected);
        }
        return msg.length() > MAX_LENGTH ? msg.substring(0, MAX_LENGTH) + "..." : msg;
    }

    /**
     * True when {@code text} cannot be shown on the one-line command bar: it carries a newline or
     * exceeds {@link #MAX_LENGTH}. Shared by the syntax-error panel decision and the contextual
     * console-notice channel (D1), so both agree on when the scrollable panel opens.
     */
    public static boolean needsPanel(String text) {
        return text != null && (text.contains("\n") || text.length() > MAX_LENGTH);
    }
}
