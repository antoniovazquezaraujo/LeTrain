package letrain.soundscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The target mix for the next tick: the volume of every audible sound plus, for each one, how far
 * away it is (0 = next to the listener, 1 = far away). The player turns distance into air
 * absorption (a low-pass filter), so distance changes the timbre and not only the level.
 */
public record Composition(Map<String,Float>volumes,Map<String,Float>distance){

public Composition{volumes=Collections.unmodifiableMap(new LinkedHashMap<>(volumes));distance=distance==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(distance));}

/** A composition without distance, for callers that only care about volumes. */
public Composition(Map<String,Float>volumes){this(volumes,Map.of());}

public float volumeOf(String sound){return volumes.getOrDefault(sound,0f);}

/** Distance of a sound; 0 when it is next to the listener. */
public float distanceOf(String sound){return distance.getOrDefault(sound,0f);}}
