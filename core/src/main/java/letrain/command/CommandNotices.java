package letrain.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Problem notices gathered while one typed console command runs (D1 contextual channel).
 *
 * <p>
 * A typed command reports on the console: a short single-line set is shown on the command line
 * ({@code Model#setCommandNotice}), while a long or multiline set opens the same scrollable panel
 * the syntax errors use ({@code Model#reportUserMessage}). The two channels are mutually exclusive,
 * so the same notice is never shown twice.
 *
 * <p>
 * Asynchronous DSL events (a program being applied, a trigger firing later, a mission failing
 * during the simulation) never go through this class: they report straight to the panel.
 */
public final class CommandNotices {

    private static final String SHORT_SEPARATOR = " | ";
    private static final String PANEL_SEPARATOR = "\n";

    private final List<String> texts = new ArrayList<>();

    /** Records a notice; a null/empty text is ignored (there is nothing to show). */
    public void add(String title, String text) {
        if (text != null && !text.isEmpty()) {
            texts.add(text);
        }
    }

    public boolean isEmpty() {
        return texts.isEmpty();
    }

    /** One-line form for the command bar; each notice text already carries its own context. */
    public String shortText() {
        return String.join(SHORT_SEPARATOR, texts);
    }

    /** Full form for the scrollable panel when the set does not fit the command bar. */
    public String panelText() {
        return String.join(PANEL_SEPARATOR, texts);
    }

    /** Same rule the syntax-error path uses: long or multiline text opens the panel. */
    public boolean needsPanel() {
        return SyntaxMessages.needsPanel(shortText());
    }
}
