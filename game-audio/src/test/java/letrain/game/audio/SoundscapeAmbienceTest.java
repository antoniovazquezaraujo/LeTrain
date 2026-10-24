package letrain.game.audio;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.time.impl.SimpleGameClock;
import letrain.vehicle.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape ambience glue")
class SoundscapeAmbienceTest {

    @Test
    @DisplayName("builds and updates the ambient mix without an audio device")
    void should_BuildAndUpdate_WithoutAudioDevice() throws IOException {
        Model model = mock(Model.class);
        GroundMap groundMap = mock(GroundMap.class);
        Cursor cursor = mock(Cursor.class);
        when(model.getGroundMap()).thenReturn(groundMap);
        when(model.getMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getEffectiveMode()).thenReturn(Model.GameMode.RAILS);
        when(model.getCursor()).thenReturn(cursor);
        when(cursor.getPosition()).thenReturn(new Point(0, 0));
        when(model.getGameClock()).thenReturn(new SimpleGameClock());

        SoundscapeAmbience ambience = new SoundscapeAmbience(model);
        ambience.update(model, 0.4f);
        ambience.close();
    }

    @Test
    @DisplayName("#805: updating with a new model samples its map and clock, never the boot ones")
    void should_UseTheCurrentModel_AfterANewGame() throws IOException {
        // Arrange: the ambience is built with the boot model, then a New Game swaps the world.
        Model boot = mock(Model.class);
        GroundMap bootMap = mock(GroundMap.class);
        when(boot.getGroundMap()).thenReturn(bootMap);

        Model newGame = mock(Model.class);
        GroundMap newMap = mock(GroundMap.class);
        Cursor cursor = mock(Cursor.class);
        when(cursor.getPosition()).thenReturn(new Point(0, 0));
        when(newGame.getGroundMap()).thenReturn(newMap);
        when(newGame.getMode()).thenReturn(Model.GameMode.RAILS);
        when(newGame.getEffectiveMode()).thenReturn(Model.GameMode.RAILS);
        when(newGame.getCursor()).thenReturn(cursor);
        when(newGame.getGameClock()).thenReturn(new SimpleGameClock());

        // Act
        SoundscapeAmbience ambience = new SoundscapeAmbience(boot);
        ambience.update(newGame, 0.4f);
        ambience.close();

        // Assert: only the current world is sampled and only its clock drives the weather.
        verify(newMap, atLeastOnce()).getValueAt(anyInt(), anyInt());
        verify(bootMap, never()).getValueAt(anyInt(), anyInt());
        verify(newGame, atLeastOnce()).getGameClock();
        verify(boot, never()).getGameClock();
    }
}
