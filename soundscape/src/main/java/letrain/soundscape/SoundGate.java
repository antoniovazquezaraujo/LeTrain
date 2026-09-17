package letrain.soundscape;

/**
 * A behaviour gate: the sound stops when the state variable goes above the threshold. Crickets do
 * not "sing quieter" in the rain, they stop; the engine feathers the transition over a small band
 * so the edge is smooth instead of a click.
 */
public record SoundGate(String variable,float threshold){

public SoundGate{if(variable==null||variable.isBlank()){throw new IllegalArgumentException("gate variable must not be blank");}if(threshold<0f){throw new IllegalArgumentException("gate threshold must be >= 0");}}}
