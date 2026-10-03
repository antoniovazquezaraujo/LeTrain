package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Command journal applied/base cursor semantics")
class CommandJournalStateTest {

    @Test
    @DisplayName("record appends and marks applied; appliedEntries is the exported slice")
    void record_marksApplied() {
        CommandJournal j = new CommandJournal();
        j.record("a");
        j.record("b");
        assertEquals(2, j.size());
        assertEquals(2, j.applied());
        assertEquals(0, j.base());
        assertEquals(java.util.List.of("a", "b"), j.appliedEntries());
    }

    @Test
    @DisplayName("undo/redo move the cursor; new record truncates the redo tail")
    void undoRedo_truncation() {
        CommandJournal j = new CommandJournal();
        j.record("a");
        j.record("b");
        j.record("c");

        j.undo(1);
        assertEquals(2, j.applied());
        assertEquals(java.util.List.of("a", "b"), j.appliedEntries());

        j.redo(1);
        assertEquals(3, j.applied());

        j.undo(2);
        j.record("d");
        assertEquals(java.util.List.of("a", "d"), j.appliedEntries(),
                "recording after undo must drop the redo tail");
        assertEquals(2, j.size());
    }

    @Test
    @DisplayName("consecutive limit tweaks for the same signal collapse into the last one")
    void coalesce_consecutiveLimits() {
        CommandJournal j = new CommandJournal();
        j.record("go 7,0; face e; signal 1 set limit 3;");
        j.recordCoalescing("go 7,0; face e; signal 1 set limit 4;");
        j.recordCoalescing("go 7,0; face e; signal 1 set limit 5;");
        assertEquals(1, j.size());
        assertEquals(java.util.List.of("go 7,0; face e; signal 1 set limit 5;"),
                j.appliedEntries());
    }

    @Test
    @DisplayName("limit tweaks only collapse when consecutive and for the same signal")
    void coalesce_onlyConsecutiveSameSignal() {
        CommandJournal j = new CommandJournal();
        j.recordCoalescing("go 0,0; face e; signal 1 set limit 3;");
        j.record("go 0,0; face e; signal 1 invert;"); // breaks the run
        j.recordCoalescing("go 0,0; face e; signal 1 set limit 5;");
        j.recordCoalescing("go 0,0; face e; signal 2 set limit 5;"); // different signal
        assertEquals(4, j.size());
    }

    @Test
    @DisplayName("consecutive fork route sets for the same fork collapse into the last one")
    void coalesce_forkRoute() {
        CommandJournal j = new CommandJournal();
        j.recordCoalescing("fork 1 set curved;");
        j.recordCoalescing("fork 1 set straight;");
        j.recordCoalescing("fork 1 set curved;");
        assertEquals(1, j.size());
        assertEquals(java.util.List.of("fork 1 set curved;"), j.appliedEntries());
        // A different fork starts a new entry.
        j.recordCoalescing("fork 2 set curved;");
        assertEquals(2, j.size());
    }

    @Test
    @DisplayName("consecutive mode sets collapse, but different properties of the same signal do not")
    void coalesce_signalMode_andSeparateProperties() {
        CommandJournal j = new CommandJournal();
        j.recordCoalescing("signal 1 set mode max;");
        j.recordCoalescing("signal 1 set mode min;");
        assertEquals(1, j.size(), "same property must collapse");
        assertEquals(java.util.List.of("signal 1 set mode min;"), j.appliedEntries());

        j.recordCoalescing("signal 1 set limit 5;"); // different property -> new entry
        assertEquals(2, j.size());
    }

    @Test
    @DisplayName("base stops undo and bake commits the applied commands")
    void base_and_bake() {
        CommandJournal j = new CommandJournal();
        j.record("base1");
        j.record("base2");
        j.bake();
        assertEquals(2, j.base());

        j.record("edit1");
        j.record("edit2");
        j.undo(5); // cannot cross the base
        assertEquals(2, j.applied());
        assertEquals(java.util.List.of("base1", "base2"), j.appliedEntries());

        j.redo(5);
        j.bake();
        assertEquals(4, j.base());
        assertEquals(4, j.applied());
    }
}
