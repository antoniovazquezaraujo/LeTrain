package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.ArrayList;
import java.util.List;
import letrain.command.GrammarReference;
import letrain.command.Placeholders;
import letrain.command.ProgramTextEdit;
import letrain.command.ProgramTextEdit.Insertion;
import letrain.command.ProgramTextEdit.Span;

/**
 * Multi-line program editor with the shared #828 editing behaviour: Tab/Shift+Tab, auto-indent,
 * placeholder caret tracking (Lanterna 3.1.5 has no selection API) and scope checks.
 */
final class ProgramTextBox extends TextBox {

    private final Runnable onChange;
    private List<Span> placeholders = List.of();
    private Span active;

    ProgramTextBox(TerminalSize size, String initialText, Runnable onChange) {
        super(size, initialText, Style.MULTI_LINE);
        this.onChange = onChange;
    }

    /**
     * Inserts a quick-reference snippet at the caret. Returns {@code null} on success, or the
     * message to show when the command does not belong to the current scope.
     */
    String insertSnippet(String snippet, GrammarReference.Scope scope) {
        int caret = caretOffset();
        int depth = ProgramTextEdit.braceDepth(getText(), caret);
        if (!ProgramTextEdit.isInsertionAllowed(scope, depth)) {
            return ProgramTextEdit.mismatchMessage(scope);
        }
        Insertion insertion = ProgramTextEdit.insert(getText(), caret, snippet);
        placeholders = new ArrayList<>(insertion.placeholders());
        active = placeholders.isEmpty() ? null : placeholders.get(0);
        setText(insertion.text());
        setCaretOffset(insertion.caret());
        onChange.run();
        return null;
    }

    /** Active placeholder plus its position, for the status bar; empty when there is none. */
    String placeholderStatus() {
        if (active == null || !isActiveValid()) {
            return "";
        }
        String token = getText().substring(active.start(), active.end());
        int index = placeholders.indexOf(active) + 1;
        return token + " " + index + "/" + placeholders.size();
    }

    @Override
    public Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        KeyType type = keyStroke.getKeyType();

        if (keyStroke.isCtrlDown() && (type == KeyType.Tab || type == KeyType.ReverseTab)) {
            return type == KeyType.Tab ? Interactable.Result.MOVE_FOCUS_NEXT
                    : Interactable.Result.MOVE_FOCUS_PREVIOUS;
        }
        if (type == KeyType.F6) {
            return Interactable.Result.MOVE_FOCUS_NEXT;
        }

        if (type == KeyType.Tab && !keyStroke.isAltDown()) {
            if (active != null && isActiveValid()) {
                cycle(true);
            } else {
                insertRaw("  ");
            }
            return Interactable.Result.HANDLED;
        }
        if (type == KeyType.ReverseTab && !keyStroke.isAltDown()) {
            if (active != null && isActiveValid()) {
                cycle(false);
            } else {
                dedentCurrentLine();
            }
            return Interactable.Result.HANDLED;
        }

        if (type == KeyType.Enter) {
            active = null;
            insertRaw("\n" + ProgramTextEdit.indentForNewLine(getText(), caretOffset()));
            return Interactable.Result.HANDLED;
        }

        if (active != null && isActiveValid()) {
            if (type == KeyType.Character && keyStroke.getCharacter() != null
                    && !keyStroke.isCtrlDown() && !keyStroke.isAltDown()) {
                replaceActive(String.valueOf(keyStroke.getCharacter()));
                return Interactable.Result.HANDLED;
            }
            if (type == KeyType.Backspace || type == KeyType.Delete) {
                replaceActive("");
                return Interactable.Result.HANDLED;
            }
        }

        Interactable.Result result = super.handleKeyStroke(keyStroke);
        if (active != null && !isActiveValid()) {
            active = null;
        }
        onChange.run();
        return result;
    }

    private void cycle(boolean forward) {
        Span next = forward ? ProgramTextEdit.nextPlaceholder(placeholders, active)
                : ProgramTextEdit.previousPlaceholder(placeholders, active);
        if (next != null) {
            active = next;
            setCaretOffset(next.start());
        }
        onChange.run();
    }

    private void replaceActive(String replacement) {
        if (active == null) {
            return;
        }
        int caret = active.start() + replacement.length();
        String updated = ProgramTextEdit.replace(getText(), active, replacement);
        placeholders = List.of();
        active = null;
        setText(updated);
        setCaretOffset(caret);
        onChange.run();
    }

    private void insertRaw(String text) {
        int caret = caretOffset();
        String updated = getText().substring(0, caret) + text + getText().substring(caret);
        setText(updated);
        setCaretOffset(caret + text.length());
        onChange.run();
    }

    private void dedentCurrentLine() {
        int caret = caretOffset();
        if (caret == 0 && getText().isEmpty()) {
            return;
        }
        String text = getText();
        int lineStart = ProgramTextEdit.lineStart(text, caret);
        String before = ProgramTextEdit.lineIndent(text, lineStart);
        String updated = ProgramTextEdit.dedentLines(text, caret, caret);
        String after = ProgramTextEdit.lineIndent(updated, lineStart);
        int removed = Math.max(0, before.length() - after.length());
        setText(updated);
        setCaretOffset(Math.max(lineStart, caret - removed));
        onChange.run();
    }

    private boolean isActiveValid() {
        if (active == null) {
            return false;
        }
        String text = getText();
        if (active.end() > text.length()) {
            return false;
        }
        int caret = caretOffset();
        if (caret < active.start() || caret > active.end()) {
            return false;
        }
        String token = text.substring(active.start(), active.end());
        return token.startsWith("<") && token.endsWith(">")
                && Placeholders.isAllowedToken(token.substring(1, token.length() - 1));
    }

    private int caretOffset() {
        TerminalPosition caret = getCaretPosition();
        String[] lines = getText().split("\n", -1);
        int row = Math.max(0, Math.min(caret.getRow(), lines.length - 1));
        int offset = 0;
        for (int i = 0; i < row; i++) {
            offset += lines[i].length() + 1;
        }
        return Math.min(getText().length(), offset + Math.max(0, caret.getColumn()));
    }

    private void setCaretOffset(int offset) {
        String text = getText();
        int at = Math.max(0, Math.min(offset, text.length()));
        int row = 0;
        int lineStart = 0;
        for (int i = 0; i < at; i++) {
            if (text.charAt(i) == '\n') {
                row++;
                lineStart = i + 1;
            }
        }
        setCaretPosition(row, at - lineStart);
    }
}
