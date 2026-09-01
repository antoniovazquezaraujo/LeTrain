package letrain.mvp.input;

import java.util.Objects;

/**
 * Neutral input abstraction carrying character / arrow key / key type info.
 * Replaces the tight coupling to lanterna's KeyStroke in the shared core.
 */
public class InputEvent {
    private final KeyType keyType;
    private final Character character;
    private final boolean ctrlDown;
    private final boolean altDown;
    private final boolean shiftDown;

    public InputEvent(KeyType keyType) {
        this(keyType, null, false, false, false);
    }

    public InputEvent(KeyType keyType, boolean ctrlDown, boolean altDown, boolean shiftDown) {
        this(keyType, null, ctrlDown, altDown, shiftDown);
    }

    public InputEvent(Character character, boolean ctrlDown, boolean altDown) {
        this(KeyType.Character, character, ctrlDown, altDown, false);
    }

    public InputEvent(KeyType keyType, Character character, boolean ctrlDown, boolean altDown, boolean shiftDown) {
        this.keyType = Objects.requireNonNull(keyType, "keyType must not be null");
        this.character = character;
        this.ctrlDown = ctrlDown;
        this.altDown = altDown;
        this.shiftDown = shiftDown;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public Character getCharacter() {
        return character;
    }

    public boolean isCtrlDown() {
        return ctrlDown;
    }

    public boolean isAltDown() {
        return altDown;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }
}
