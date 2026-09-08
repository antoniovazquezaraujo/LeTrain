package letrain.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory command journal (ADR-020 roadmap, item 2): an ordered list of the DSL commands the
 * player executed while recording was on. The journal itself is passive — it only stores what the
 * recorder feeds it ({@link #record(String)}). Consumers replay it later on a fresh same-seed world
 * to reproduce the edited state (scenario export and deterministic undo both build on this).
 *
 * <p>
 * The journal does not know whether the world is paused; that guarantee lives in the caller
 * (pause-editing keeps the simulation frozen so only the journaled edits mutate the world).
 */
public class CommandJournal {

    private final List<String> commands = new ArrayList<>();
    private boolean recording = false;

    /** True while the recorder accepts commands (after {@code record on}). */
    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        recording = true;
    }

    public void stopRecording() {
        recording = false;
    }

    public void toggleRecording() {
        recording = !recording;
    }

    public void record(String command) {
        if (command != null && !command.trim().isEmpty()) {
            commands.add(command.trim());
        }
    }

    public List<String> entries() {
        return Collections.unmodifiableList(commands);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public int size() {
        return commands.size();
    }

    public void clear() {
        commands.clear();
    }
}
