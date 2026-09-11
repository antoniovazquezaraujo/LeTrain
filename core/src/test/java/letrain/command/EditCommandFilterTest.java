package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Recorder filter: navigation/control vs edits")
class EditCommandFilterTest {

    @Test
    @DisplayName("pure navigation and control lines are not recordable")
    void navigationAndControl_areNotRecordable() {
        assertTrue(EditCommandFilter.isNonRecordable("go 5,5;"));
        assertTrue(EditCommandFilter.isNonRecordable("go 5,5; face e;"));
        assertTrue(EditCommandFilter.isNonRecordable("move 3;"));
        assertTrue(EditCommandFilter.isNonRecordable("go mark home;"));
        assertTrue(EditCommandFilter.isNonRecordable("face mark home;"));
        assertTrue(EditCommandFilter.isNonRecordable("undo;"));
        assertTrue(EditCommandFilter.isNonRecordable("ls;"));
        assertTrue(EditCommandFilter.isNonRecordable("save foo;"));
        assertTrue(EditCommandFilter.isNonRecordable(""));
        assertTrue(EditCommandFilter.isNonRecordable(null));
    }

    @Test
    @DisplayName("an edit is recordable even when the line also navigates")
    void editWithNavigation_isRecordable() {
        assertFalse(EditCommandFilter.isNonRecordable("new sg;"));
        assertFalse(EditCommandFilter.isNonRecordable("go 0,0; face e; new sg;"));
        assertFalse(EditCommandFilter.isNonRecordable("go 7,0; face e; write 1;"));
        assertFalse(EditCommandFilter.isNonRecordable("go 7,0; face e; signal 1 set limit 4;"));
        assertFalse(EditCommandFilter.isNonRecordable("go 7,0; face e; slide sn 1 fw 2;"));
        // Setting a mark is state (so 'go mark' is reproducible on replay).
        assertFalse(EditCommandFilter.isNonRecordable("mark home;"));
        assertFalse(EditCommandFilter.isNonRecordable("m home;"));
        assertFalse(EditCommandFilter.isNonRecordable("go mark home; new sn;"));
    }

    @Test
    @DisplayName("a control command anywhere makes the line non-recordable")
    void controlAnywhere_isNotRecordable() {
        assertTrue(EditCommandFilter.isNonRecordable("go 0,0; undo;"));
    }
}
