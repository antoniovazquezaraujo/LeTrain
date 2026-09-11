package letrain.command;

/**
 * Classifies console command lines for the recorders (command journal and undo history, ADR-020).
 *
 * <p>A line is <b>non-recordable</b> when it changes no world state: pure navigation ({@code go},
 * {@code face}, {@code move}) or control/info commands ({@code undo}, {@code save}, ...). A line that
 * mixes navigation with an edit — e.g. {@code go 0,0; face e; new sg;} — IS recordable: the funnel
 * stores the canonical self-positioned form, so recording the whole line is deterministic. The
 * earlier check looked only at the first token, so any line starting with {@code go} was dropped even
 * when it contained an edit.
 *
 * <p>{@code mark x;} (and its alias {@code m x;}) is treated as an edit: it changes model state (a
 * named position). Recording it keeps {@code go mark x;} reproducible in a replayed scenario.
 */
public final class EditCommandFilter {

    private EditCommandFilter() {}

    /** True when the line only moves the cursor or controls the app (nothing to undo/export). */
    public static boolean isNonRecordable(String cmd) {
        if (cmd == null) {
            return true;
        }
        boolean hasEdit = false;
        for (String rawStatement : cmd.toLowerCase().split(";")) {
            String statement = rawStatement.trim();
            if (statement.isEmpty()) {
                continue;
            }
            if (isControl(statement)) {
                return true;
            }
            if (!isNavigation(statement)) {
                hasEdit = true;
            }
        }
        return !hasEdit;
    }

    private static boolean isControl(String statement) {
        return statement.startsWith("record") || statement.startsWith("journal")
                || statement.startsWith("undo") || statement.startsWith("redo")
                || statement.startsWith("export") || statement.startsWith("import")
                || statement.startsWith("ls") || statement.startsWith("info")
                || statement.startsWith("save") || statement.startsWith("load")
                || statement.startsWith("quit") || statement.equals("q")
                || statement.startsWith("q!") || statement.startsWith("wq")
                || statement.startsWith("help");
    }

    private static boolean isNavigation(String statement) {
        return statement.startsWith("go") || statement.startsWith("face")
                || statement.startsWith("move");
    }
}
