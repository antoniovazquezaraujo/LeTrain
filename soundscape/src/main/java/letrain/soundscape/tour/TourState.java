package letrain.soundscape.tour;

import java.util.Map;

public record TourState(Map<String,Float>zones,float enclosure,float rain,float wind,float storm,String weather){

public TourState{zones=zones==null?Map.of():Map.copyOf(zones);}

public float zoneWeight(String zone){return zones.getOrDefault(zone,0f);}}
