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

    private SoundSample highs() {
        float[] data = new float[44100];
        for (int i = 0; i < data.length; i++) {
            data[i] = i % 2 == 0 ? 0.5f : -0.5f;
        }
        return new SoundSample("highs.wav", data, 44100f);
    }

    /** Mean sample-to-sample change: a proxy of how much high frequency the voice carries. */
    private float brightness(AmbientVoice voice, float[] mono) {
        java.util.Arrays.fill(mono, 0f);
        voice.render(mono, mono.length);
        float sum = 0f;
        for (int i = 1; i < mono.length; i++) {
            sum += Math.abs(mono[i] - mono[i - 1]);
        }
        return sum / (mono.length - 1);
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
    @DisplayName("distance distance muffles the high frequencies")
    void should_MuffleHighs_When_DistanceIsSet() {
        AmbientVoice near = new AmbientVoice(highs());
        near.setTarget(0.5f);
        AmbientVoice far = new AmbientVoice(highs());
        far.setTarget(0.5f);
        far.setDistance(1f);

        float[] mono = new float[FRAMES];
        for (int i = 0; i < 200; i++) {
            renderMax(near, mono);
            renderMax(far, mono);
        }
        float nearBrightness = brightness(near, mono);
        float farBrightness = brightness(far, mono);

        assertTrue(farBrightness < nearBrightness * 0.25f,
                "distance should muffle highs: near=" + nearBrightness + " far=" + farBrightness);
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
