package letrain.soundscape;

/**
 * Computes the target mix for a style and an ambient state. The result is a deterministic function
 * of its inputs: same inputs, same composition. It does not touch audio devices, files or the game.
 */
public interface SoundscapeEngine {

    /**
     * @param style the parsed style (zones, presence curves, sensitivities)
     * @param input the ambient state (time, zone weights, height, weather)
     * @return the target volume of every audible sound
     */
    Composition compose(SoundscapeStyle style, CompositionInput input);
}
