package letrain.soundscape;

import java.util.Map;

/** A day-of-year range with its climate probabilities, as declared in the style's [seasons]. */
public record SeasonRange(int startDay,int endDay,Map<String,Float>probabilities){

public SeasonRange{probabilities=Map.copyOf(probabilities);}}
