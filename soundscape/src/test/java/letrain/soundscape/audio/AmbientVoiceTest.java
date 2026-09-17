package letrain.soundscape.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ambient voice")
class AmbientVoiceTest {

    private static final int FRAMES = 1024;

    private SoundSample tone(int lengthFrames) {
        float[] data = new float[lengthFrames];
        for (int i = 0; i < lengthFrames; i++) {
            data[i] = (float) (0.5 * Math.sin(2 * Math.PI * 440 * i / 44100.0));
        }
        return new SoundSample("tone.wav", data, 44100f);
    }

    private float renderMax(AmbientVoice voice, float[] mono) {
        java.util.Arrays.fill(mono, 0f);
        voice.render(mono, mono.length);
        float max = 0f;
        for (float value : mono) {
            assertFalse(Float.isNaN(value), "mix must not contain NaN");
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    @Test
    @DisplayName("fades in and reaches the target volume")
    void should_FadeIn_When_TargetIsSet() {
        AmbientVoice voice = new AmbientVoice(tone(44100));
        voice.setTarget(0.5f);

        float first = renderMax(voice, new float[FRAMES]);
        float last = 0f;
        for (int i = 0; i < 200; i++) {
            last = renderMax(voice, new float[FRAMES]);
        }

        assertTrue(first < last, "gain should rise over time");
        assertTrue(last > 0.2f, "should approach the target volume, was " + last);
    }

    @Test
    @DisplayName("loops the whole sample with bounded output")
    void should_LoopWholeSample_When_ReachingTheEnd() {
        AmbientVoice voice = new AmbientVoice(tone(4410));
        voice.setTarget(0.5f);

        for (int i = 0; i < 50; i++) {
            float max = renderMax(voice, new float[FRAMES]);
            assertTrue(max <= 0.51f, "looping mix must stay bounded, was " + max);
        }
    }

    @Test
    @DisplayName("becomes finished after fading out")
    void should_Finish_When_TargetRemoved() {
        AmbientVoice voice = new AmbientVoice(tone(44100));
        voice.setTarget(0.5f);
        for (int i = 0; i < 50; i++) {
            renderMax(voice, new float[FRAMES]);
        }
        assertFalse(voice.isFinished());

        voice.setTarget(0f);
        for (int i = 0; i < 500 && !voice.isFinished(); i++) {
            renderMax(voice, new float[FRAMES]);
        }
        assertTrue(voice.isFinished());
    }
}
