package letrain.audio.core;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioMixer {

    private static final Logger log = LoggerFactory.getLogger(AudioMixer.class);

    private final Collection<AudioSource> sources = new ConcurrentLinkedQueue<>();
    private volatile boolean running = false;
    private Thread audioThread;

    // Audio Format Constants
    public static final float SAMPLE_RATE = 44100.0f;
    public static final int BUFFER_SIZE = 1024;

    // Listener Position (The Camera)
    private float listenerX = 0;
    private float listenerY = 0;
    private float listenerZ = 0;
    private float listenerAngle = 0; // In radians, 0 = facing North/Up? TBD

    public void addSource(AudioSource source) {
        sources.add(source);
    }

    public void removeSource(AudioSource source) {
        sources.remove(source);
    }

    public void start() {
        if (running)
            return;
        running = true;
        audioThread = new Thread(this::audioLoop, "AudioMixerThread");
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (audioThread != null)
                audioThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Audio mixer stop interrupted", e);
        }
    }

    public void setListenerPosition(float x, float y, float z) {
        this.listenerX = x;
        this.listenerY = y;
        this.listenerZ = z;
    }

    public void setListenerAngle(float angleRadians) {
        this.listenerAngle = angleRadians;
    }

    private void audioLoop() {
        SourceDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, true); // Stereo
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, BUFFER_SIZE * 4); // 4096 frames
            line.start();

            // Buffers
            // Independent buffers for each source would be ideal to avoid allocation in
            // loop,
            // but for simplicity we'll let sources write to a temp buffer and mix.
            // Actually, AudioSource.read(float[]) expects a buffer.
            // We need a mixing buffer (float) and an output buffer (byte).

            // Stereo buffer: L, R, L, R...
            float[] mixBuffer = new float[BUFFER_SIZE * 2];
            float[] sourceBuffer = new float[BUFFER_SIZE]; // Mono source buffer
            byte[] outputBuffer = new byte[BUFFER_SIZE * 4];

            while (running) {
                try {
                    // Clear mix buffer
                    for (int i = 0; i < mixBuffer.length; i++)
                        mixBuffer[i] = 0;

                    // Mix sources
                    for (AudioSource source : sources) {
                        // Reset source buffer
                        for (int i = 0; i < sourceBuffer.length; i++)
                            sourceBuffer[i] = 0;

                        boolean active = source.read(sourceBuffer);
                        if (!active) {
                            if (source.isFinished()) {
                                sources.remove(source);
                            }
                            continue;
                        }

                        // 2. Calculate Distance & Pan
                        float dx = source.getX() - listenerX;
                        float dy = source.getY() - listenerY;
                        float dz = source.getZ() - listenerZ;

                        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                        // Improved Logarithmic/Inverse-Square-like Attenuation
                        float refDist = source.getReferenceDistance();
                        float maxDist = source.getMaxDistance();

                        float volume = 1.0f;
                        if (distance > refDist) {
                            volume = refDist / distance;
                            if (distance > maxDist) {
                                float fadeFactor = 1.0f - ((distance - maxDist) / (maxDist * 0.2f));
                                volume *= Math.max(0.0f, fadeFactor);
                            }
                        }
                        if (volume > 1.0f)
                            volume = 1.0f;
                        if (volume < 0.0f)
                            volume = 0.0f;

                        // NaN protection for volume
                        if (Float.isNaN(volume) || Float.isInfinite(volume))
                            volume = 0;

                        // 5. Apply to Mix (Mono to Stereo)
                        for (int i = 0; i < BUFFER_SIZE; i++) {
                            float sample = sourceBuffer[i];
                            // NaN protection for sample
                            if (Float.isNaN(sample) || Float.isInfinite(sample))
                                sample = 0;

                            mixBuffer[i * 2] += sample * volume;
                            mixBuffer[i * 2 + 1] += sample * volume;
                        }
                    }

                    // Limit/Clip and Convert to Bytes
                    for (int i = 0; i < BUFFER_SIZE * 2; i++) {
                        float val = mixBuffer[i];
                        if (Float.isNaN(val) || Float.isInfinite(val))
                            val = 0;
                        if (val > 1.0f)
                            val = 1.0f;
                        if (val < -1.0f)
                            val = -1.0f;

                        short s = (short) (val * 32767.0f);
                        outputBuffer[i * 2] = (byte) ((s >> 8) & 0xFF);
                        outputBuffer[i * 2 + 1] = (byte) (s & 0xFF);
                    }

                    line.write(outputBuffer, 0, outputBuffer.length);
                } catch (Exception e) {
                    // Prevent thread death on single source error
                    log.error("AudioMixer error in mixing loop", e);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            try {
                line.stop();
                line.flush();
            } catch (Exception e) {
                log.warn("Error stopping/flushing audio line", e);
            }
        } catch (LineUnavailableException e) {
            log.error("AudioMixer failed to initialize audio line", e);
        } finally {
            if (line != null) {
                try {
                    line.close();
                } catch (Exception e) {
                    log.warn("Error closing audio line", e);
                }
            }
        }
    }
}
