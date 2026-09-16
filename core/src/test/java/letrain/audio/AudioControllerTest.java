package letrain.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import letrain.audio.core.AudioMixer;
import letrain.audio.synth.TrainSynthesizer;
import letrain.mvp.Model;
import letrain.vehicle.rail.TrainLogisticsManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for issue #360: turning the engine off while loading used to detach the
 * synthesizer from the update loop, so the loading sound kept looping at the station forever.
 */
@DisplayName("AudioController - engine off during load")
class AudioControllerTest {

    private Model model;
    private Locomotive loco;
    private Train train;
    private TrainLogisticsManager logistics;
    private AudioController controller;

    @BeforeEach
    void setUp() {
        model = mock(Model.class);
        loco = mock(Locomotive.class);
        train = mock(Train.class);
        logistics = mock(TrainLogisticsManager.class);
        // Injected mixer is never started: no audio device or audio thread needed.
        controller = new AudioController(model, new AudioMixer());
    }

    @Test
    @DisplayName("keeps the synthesizer attached while the engine stop sound plays")
    void should_KeepSynthesizerAttached_When_EngineTurnedOffDuringLoad() {
        givenEngineOnLoadingTrain(true);

        controller.update();
        TrainSynthesizer synth = controller.getSynthesizer(1);
        assertNotNull(synth, "synth should be created for an engine-on locomotive");
        assertTrue(synth.isLoading(), "loading state should be synced while loading");

        when(loco.isEngineOn()).thenReturn(false);
        controller.stopEngineWithSound(1, loco);

        assertNotNull(controller.getSynthesizer(1),
                "synth must stay attached so update() can drive the stop sound");
        assertTrue(synth.isStopping(), "the stop sound should be playing");
    }

    @Test
    @DisplayName("keeps the load loop alive after the stop sound while the load still runs")
    void should_KeepLoadLoopAlive_When_StopSoundEndsWithLoadRunning() {
        givenEngineOnLoadingTrain(true);
        controller.update();

        when(loco.isEngineOn()).thenReturn(false);
        controller.stopEngineWithSound(1, loco);

        TrainSynthesizer synth = controller.getSynthesizer(1);
        synth.update(10f); // deterministic: finish the stop segment

        assertTrue(synth.isLoadOnly(),
                "with the engine off the synth must keep sounding only the load loop");
        assertNotNull(controller.getSynthesizer(1), "synth must remain attached");
        assertTrue(synth.isLoading(), "load state must survive the engine toggle");
    }

    @Test
    @DisplayName("retires the synthesizer once the load ends and fades out with the engine off")
    void should_RetireSynthesizer_When_LoadEndsAndFadesWithEngineOff() {
        givenEngineOnLoadingTrain(true);
        controller.update();

        when(loco.isEngineOn()).thenReturn(false);
        controller.stopEngineWithSound(1, loco);
        TrainSynthesizer synth = controller.getSynthesizer(1);
        synth.update(10f); // stop sound finished -> LOAD_ONLY

        when(logistics.isLoading()).thenReturn(false);
        controller.update();
        assertFalse(synth.isLoading(), "loading state must reset when the load ends");

        float[] buffer = new float[2048];
        for (int i = 0; i < 200 && synth.isLoadSoundActive(); i++) {
            synth.read(buffer); // audio thread ramps the load volume down
        }
        assertFalse(synth.isLoadSoundActive(), "load loop should be silent after the fade");

        controller.update();
        assertNull(controller.getSynthesizer(1),
                "silent synth must be retired so it cannot sound again at the station");
    }

    @Test
    @DisplayName("restarts the engine without dropping the load when switched on while stopping")
    void should_RestartEngine_When_SwitchedOnWhileStoppingDuringLoad() {
        givenEngineOnLoadingTrain(true);
        controller.update();

        when(loco.isEngineOn()).thenReturn(false);
        controller.stopEngineWithSound(1, loco);
        assertTrue(controller.getSynthesizer(1).isStopping());

        when(loco.isEngineOn()).thenReturn(true);
        controller.update();

        TrainSynthesizer synth = controller.getSynthesizer(1);
        assertNotNull(synth, "synth must stay attached after restarting the engine");
        assertFalse(synth.isStopping(), "stop sound must be cancelled by the engine restart");
        assertTrue(synth.isEngineRunning(), "engine voice must be running again");
        assertTrue(synth.isLoading(), "load state must be preserved while the load runs");
    }

    @Test
    @DisplayName("retires the synthesizer after the stop sound when there is no load running")
    void should_RetireSynthesizer_When_StopSoundEndsWithoutLoad() {
        givenEngineOnLoadingTrain(false);
        controller.update();

        when(loco.isEngineOn()).thenReturn(false);
        controller.stopEngineWithSound(1, loco);
        TrainSynthesizer synth = controller.getSynthesizer(1);
        assertTrue(synth.isStopping());

        synth.update(10f); // stop sound finished, no load pending -> OFF
        controller.update();

        assertNull(controller.getSynthesizer(1),
                "no zombie source may remain in the mixer after stopping the engine");
    }

    private void givenEngineOnLoadingTrain(boolean loading) {
        when(model.getLocomotives()).thenReturn(List.of(loco));
        when(loco.getId()).thenReturn(1);
        when(loco.isEngineOn()).thenReturn(true);
        when(loco.getTrain()).thenReturn(train);
        when(train.getLogisticsManager()).thenReturn(logistics);
        when(train.getDirectorLinker()).thenReturn(loco);
        when(logistics.isLoading()).thenReturn(loading);
    }
}
