package letrain.audio.material;

import java.util.Optional;
import letrain.audio.synth.AudioSample;

/**
 * Resolves a logical material id to decoded audio (ADR-029 §5).
 *
 * <p>
 * This is the seam towards the encrypted sound packs of ADR-028: a future pack implementation can
 * be composed before the classpath fallback without touching the profile descriptor, the synth or
 * the material bank.
 */
public interface MaterialResolver {

    /** Decoded sample for {@code id}, or empty when it is not resolvable. */
    Optional<AudioSample> resolve(MaterialId id);
}
