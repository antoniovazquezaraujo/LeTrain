package letrain.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Zone ambience bridge")
class ZoneAmbienceTest {

    private Model model;
    private Cursor cursor;
    private Locomotive locomotive;
    private int[][] cells;

    @BeforeEach
    void setUp() {
        model = mock(Model.class);
        when(model.getEffectiveMode()).thenAnswer(invocation -> {
            Model.GameMode mode = model.getMode();
            return mode == Model.GameMode.COMMAND ? model.getPreviousMode() : mode;
        });
        cursor = mock(Cursor.class);
        locomotive = mock(Locomotive.class);
        cells = new int[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                cells[y][x] = x < 64 ? GroundMap.WATER : GroundMap.GROUND;
            }
        }
    }

    private ZoneAmbience ambience() {
        GroundMap map = mock(GroundMap.class);
        when(map.getValueAt(anyInt(), anyInt()))
                .thenAnswer(invocation -> cells[clamp(invocation.getArgument(1))][clamp(
                        invocation.getArgument(0))]);
        return new ZoneAmbience(map, 8, 1);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(127, value));
    }

    @Test
    @DisplayName("rails mode listens from the cursor")
    void should_FollowCursor_InRails() {
        when(model.getMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getCursor()).thenReturn(cursor);
        when(cursor.getPosition()).thenReturn(new Point(66, 64));

        ZoneAmbience.Update update = ambience().update(model, 0L, true);

        assertEquals("fields", update.primary());
        assertTrue(update.weightOf("sea") > 0f);
        assertTrue(update.focusChanged());
    }

    @Test
    @DisplayName("drive mode listens from the selected locomotive")
    void should_FollowLocomotive_InDrive() {
        when(model.getMode()).thenReturn(Model.GameMode.DRIVE);
        when(model.getSelectedLocomotive()).thenReturn(locomotive);
        when(locomotive.getPosition()).thenReturn(new Point(60, 64));

        ZoneAmbience.Update update = ambience().update(model, 0L, true);

        assertEquals("sea", update.primary(), update.weights() + " " + update.influence());
        assertTrue(update.weightOf("fields") > 0f, update.weights() + " " + update.influence());
    }

    @Test
    @DisplayName("non-spatial modes keep the last focus and do not cut")
    void should_KeepLastFocus_InMenu() {
        when(model.getMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getCursor()).thenReturn(cursor);
        when(cursor.getPosition()).thenReturn(new Point(66, 64));
        ZoneAmbience ambience = ambience();
        ambience.update(model, 0L, true);

        when(model.getMode()).thenReturn(Model.GameMode.MENU);
        ZoneAmbience.Update update = ambience.update(model, 0L, true);

        assertEquals("fields", update.primary());
        assertFalse(update.focusChanged());
    }

    @Test
    @DisplayName("the console over DRIVE listens from the selected locomotive, even from a cold start")
    void should_FollowLocomotive_InConsoleOverDrive() {
        when(model.getMode()).thenReturn(Model.GameMode.COMMAND);
        when(model.getPreviousMode()).thenReturn(Model.GameMode.DRIVE);
        when(model.getSelectedLocomotive()).thenReturn(locomotive);
        when(locomotive.getPosition()).thenReturn(new Point(60, 64));

        ZoneAmbience.Update update = ambience().update(model, 0L, true);

        assertEquals("sea", update.primary(), update.weights() + " " + update.influence());
        assertTrue(update.weightOf("fields") > 0f, update.weights() + " " + update.influence());
    }

    @Test
    @DisplayName("changing focus kind reports a cut")
    void should_ReportFocusChange() {
        when(model.getMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getCursor()).thenReturn(cursor);
        when(cursor.getPosition()).thenReturn(new Point(66, 64));
        ZoneAmbience ambience = ambience();
        ambience.update(model, 0L, true);

        when(model.getMode()).thenReturn(Model.GameMode.DRIVE);
        when(model.getSelectedLocomotive()).thenReturn(locomotive);
        when(locomotive.getPosition()).thenReturn(new Point(60, 64));
        ZoneAmbience.Update update = ambience.update(model, 0L, true);

        assertEquals("sea", update.primary());
        assertTrue(update.focusChanged());
    }

    @Test
    @DisplayName("sampling is capped while the focus moves")
    void should_CapSamplingRate() {
        when(model.getMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getCursor()).thenReturn(cursor);
        when(cursor.getPosition()).thenReturn(new Point(66, 64));
        ZoneAmbience ambience = ambience();
        ZoneAmbience.Update first = ambience.update(model, 0L, false);

        when(cursor.getPosition()).thenReturn(new Point(67, 64));
        ZoneAmbience.Update capped = ambience.update(model, 50_000_000L, false);
        assertEquals(first.primary(), capped.primary());

        when(cursor.getPosition()).thenReturn(new Point(68, 64));
        ZoneAmbience.Update later = ambience.update(model, 400_000_000L, false);
        assertEquals(first.primary(), later.primary());
    }

    @Test
    @DisplayName("a missing selected entity keeps the last focus")
    void should_KeepFocus_When_SelectionMissing() {
        when(model.getMode()).thenReturn(Model.GameMode.STATIONS);
        when(model.getSelectedStation()).thenReturn(null);

        ZoneAmbience.Update update = ambience().update(model, 0L, true);

        assertNull(update.primary());
    }
}
