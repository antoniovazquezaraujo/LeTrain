package letrain.game.audio;

import static org.mockito.Mockito.mock;
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
}
