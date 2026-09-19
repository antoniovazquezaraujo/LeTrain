package letrain.soundscape.tour;

import java.util.Map;

public record TourPoint(double seconds,Map<String,Float>zones,float enclosure,float rain,float wind,float storm,String weather){

public TourPoint{zones=zones==null?Map.of():Map.copyOf(zones);}}
