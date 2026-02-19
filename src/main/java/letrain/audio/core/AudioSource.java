package letrain.audio.core;

public interface AudioSource {
    /**
     * Fills the buffer with the next chunk of audio.
     * The buffer is expected to be stereo interleaved if the mixer is stereo.
     * 
     * @param buffer The buffer to fill.
     * @return true if the source is active and produced sound, false if it's
     *         finished/silent.
     */
    boolean read(float[] buffer);

    /**
     * Sets the world position of this sound source.
     */
    void setPosition(float x, float y, float z);

    float getX();

    float getY();

    float getZ();

    /**
     * @return The distance at which the sound is at full volume.
     */
    float getReferenceDistance();

    /**
     * @return The distance at which the sound stops being heard (or is minimal).
     */
    float getMaxDistance();

    /**
     * Sets the amount of low-pass filtering due to distance.
     * 
     * @param amount 0.0 (no filter, close) to 1.0 (max filter, far)
     */
    void setDistanceFilter(float amount);
}
