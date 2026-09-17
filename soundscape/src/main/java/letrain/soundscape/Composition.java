package letrain.soundscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The target mix for the next tick: the volume of every audible sound plus, for each one, the air
 * absorption to apply (0 = next to the listener, 1 = far away). Air is translated to a low-pass
 * filter by the player, so distance changes the timbre instead of only the level.
 */
public record Composition(Map<String,Float>volumes,Map<String,Float>air){

public Composition{volumes=Collections.unmodifiableMap(new LinkedHashMap<>(volumes));air=air==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(air));}

/** A composition without air absorption, for callers that only care about volumes. */
public Composition(Map<String,Float>volumes){this(volumes,Map.of());}

public float volumeOf(String sound){return volumes.getOrDefault(sound,0f);}

/** Air absorption of a sound; 0 when it is next to the listener. */
public float airOf(String sound){return air.getOrDefault(sound,0f);}}
