package letrain.soundscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The mixed target volume for every sound that is currently audible. */
public record Composition(Map<String,Float>volumes){

public Composition{volumes=Collections.unmodifiableMap(new LinkedHashMap<>(volumes));}

public float volumeOf(String sound){return volumes.getOrDefault(sound,0f);}}
