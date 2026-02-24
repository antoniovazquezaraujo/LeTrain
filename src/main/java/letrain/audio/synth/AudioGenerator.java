package letrain.audio.synth;

public abstract class AudioGenerator {
    protected float sampleRate = 44100.0f;
    protected float volume = 1.0f;

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public float getVolume() {
        return this.volume;
    }

    /**
     * Fills the buffer with the next chunk of audio.
     * 
     * @param buffer The buffer to fill.
     */
    public abstract void read(float[] buffer);
}
