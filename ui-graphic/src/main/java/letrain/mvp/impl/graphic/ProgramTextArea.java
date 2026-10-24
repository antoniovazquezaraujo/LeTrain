package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import java.util.ArrayList;
import java.util.List;
import letrain.command.GrammarReference;
import letrain.command.Placeholders;
import letrain.command.ProgramTextEdit;
import letrain.command.ProgramTextEdit.Insertion;
import letrain.command.ProgramTextEdit.Span;

/**
 * Program editor text area with the shared #828 editing behaviour: Tab/Shift+Tab, auto-indent,
 * placeholder selection/cycling and scope checks. A capture listener runs before the native
 * TextArea listener so control keys never reach the text.
 */
final class ProgramTextArea extends TextArea {

    private Runnable onChange = () -> {
    };
    private java.util.function.Consumer<String> onMessage = message -> {
    };
    private List<Span> placeholders = List.of();
    private Span active;

    ProgramTextArea(String text, Skin skin, String styleName) {
        super(text, skin, styleName);
        addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.TAB) {
                    if (isCtrlPressed()) {
                        return false;
                    }
                    boolean shift = isShiftPressed();
                    if (active != null && isActiveValid()) {
                        cycle(!shift);
                    } else if (shift) {
                        dedent();
                    } else {
                        insertRaw("  ");
                    }
                    event.stop();
                    return true;
                }
                if (keycode == Input.Keys.ENTER) {
                    active = null;
                    String indent =
                            ProgramTextEdit.indentForNewLine(getText(), getCursorPosition());
                    insertRaw("\n" + indent);
                    event.stop();
                    return true;
                }
                if (active != null && isActiveValid()
                        && (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.DEL)) {
                    replaceActive("");
                    event.stop();
                    return true;
                }
                if (active != null && keycode != Input.Keys.TAB && keycode != Input.Keys.SHIFT_LEFT
                        && keycode != Input.Keys.SHIFT_RIGHT) {
                    if (keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT
                            || keycode == Input.Keys.UP || keycode == Input.Keys.DOWN
                            || keycode == Input.Keys.HOME || keycode == Input.Keys.END
                            || keycode == Input.Keys.PAGE_UP || keycode == Input.Keys.PAGE_DOWN
                            || keycode == Input.Keys.ESCAPE) {
                        active = null;
                    }
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                if (character == '\t' || character == '\r' || character == '\n') {
                    return true;
                }
                if (active != null && isActiveValid() && character >= 32 && character != 127) {
                    replaceActive(String.valueOf(character));
                    event.stop();
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                active = null;
                return false;
            }
        });
        setTextFieldListener((textField, c) -> {
            if (active != null && !isActiveValid()) {
                active = null;
            }
            onChange.run();
        });
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    void setOnMessage(java.util.function.Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    /**
     * Inserts a quick-reference snippet at the caret. Returns {@code null} on success, or the
     * message to show when the command does not belong to the current scope.
     */
    String insertSnippet(String snippet, GrammarReference.Scope scope) {
        if (scope != null && !ProgramTextEdit.isInsertionAllowed(scope,
                ProgramTextEdit.programBodyDepth(getText(), getCursorPosition()))) {
            String message = ProgramTextEdit.mismatchMessage(scope);
            onMessage.accept(message);
            return message;
        }
        Insertion insertion = ProgramTextEdit.insert(getText(), getCursorPosition(), snippet);
        List<Span> found = new ArrayList<>(insertion.placeholders());
        setText(insertion.text());
        // setText fires the TextFieldListener and clears any stale placeholder, so activate the
        // new span only after the programmatic update.
        placeholders = found;
        active = placeholders.isEmpty() ? null : placeholders.get(0);
        if (active != null) {
            setSelection(active.start(), active.end());
        } else {
            setCursorPosition(insertion.caret());
        }
        onChange.run();
        return null;
    }

    String placeholderStatus() {
        if (active == null || !isActiveValid()) {
            return "";
        }
        String token = getText().substring(active.start(), active.end());
        return token + " " + (placeholders.indexOf(active) + 1) + "/" + placeholders.size();
    }

    private void cycle(boolean forward) {
        Span next = forward ? ProgramTextEdit.nextPlaceholder(placeholders, active)
                : ProgramTextEdit.previousPlaceholder(placeholders, active);
        if (next != null) {
            active = next;
            setSelection(next.start(), next.end());
            onChange.run();
        }
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
        setCursorPosition(caret);
        onChange.run();
    }

    private void insertRaw(String text) {
        int start = getCursorPosition();
        int end = start;
        if (hasSelection) {
            start = Math.min(getCursorPosition(), getSelectionStart());
            end = Math.max(getCursorPosition(), getSelectionStart());
        }
        String current = getText();
        setText(current.substring(0, start) + text + current.substring(end));
        setCursorPosition(start + text.length());
        onChange.run();
    }

    private void dedent() {
        int start = getCursorPosition();
        int end = start;
        if (hasSelection) {
            start = Math.min(getCursorPosition(), getSelectionStart());
            end = Math.max(getCursorPosition(), getSelectionStart());
        }
        String text = getText();
        int lineStart = ProgramTextEdit.lineStart(text, start);
        String before = ProgramTextEdit.lineIndent(text, lineStart);
        String updated = ProgramTextEdit.dedentLines(text, start, end);
        String after = ProgramTextEdit.lineIndent(updated, lineStart);
        int removed = Math.max(0, before.length() - after.length());
        setText(updated);
        setCursorPosition(Math.max(lineStart, start - removed));
        onChange.run();
    }

    private boolean isActiveValid() {
        if (active == null) {
            return false;
        }
        String text = getText();
        if (active.end() > text.length() || !hasSelection) {
            return false;
        }
        String token = text.substring(active.start(), active.end());
        return getSelectionStart() == active.start() && token.equals(getSelection())
                && token.startsWith("<") && token.endsWith(">")
                && Placeholders.isAllowedToken(token.substring(1, token.length() - 1));
    }

    private static boolean isCtrlPressed() {
        return com.badlogic.gdx.Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || com.badlogic.gdx.Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    private static boolean isShiftPressed() {
        return com.badlogic.gdx.Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || com.badlogic.gdx.Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
}
