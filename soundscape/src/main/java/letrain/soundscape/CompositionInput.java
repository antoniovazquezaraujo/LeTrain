package letrain.soundscape;

import java.time.LocalTime;
import java.util.Map;

/**
 * Everything the game tells the soundscape in a tick: the exact time, how close each zone is
 * (weight 0.0-1.0), the listening height (zoom, 0.0-1.0) and the current weather intensities.
 */
public record CompositionInput(LocalTime time,Map<String,Float>zoneWeights,float height,float rain,float wind,float storm){

public CompositionInput{if(time==null){throw new IllegalArgumentException("time must not be null");}zoneWeights=zoneWeights==null?Map.of():Map.copyOf(zoneWeights);}}
