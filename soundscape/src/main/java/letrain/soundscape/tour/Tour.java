package letrain.soundscape.tour;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Tour(List<TourPoint>points){

public Tour{if(points==null||points.isEmpty()){throw new IllegalArgumentException("a tour needs at least one point");}points=List.copyOf(points);}

public double duration(){return points.get(points.size()-1).seconds();}

public TourState stateAt(double seconds){double t=duration()>0?seconds%duration():0;TourPoint first=points.get(0);TourPoint last=points.get(points.size()-1);if(t<=first.seconds()){return stateOf(first,first,0f);}for(int i=0;i<points.size()-1;i++){TourPoint from=points.get(i);TourPoint to=points.get(i+1);if(t>=from.seconds()&&t<to.seconds()){float alpha=(float)((t-from.seconds())/(to.seconds()-from.seconds()));return stateOf(from,to,alpha);}}return stateOf(last,last,0f);}

private TourState stateOf(TourPoint from,TourPoint to,float alpha){Set<String>keys=new LinkedHashSet<>(from.zones().keySet());keys.addAll(to.zones().keySet());Map<String,Float>zones=new LinkedHashMap<>();for(String key:keys){float a=from.zones().getOrDefault(key,0f);float b=to.zones().getOrDefault(key,0f);zones.put(key,a+(b-a)*alpha);}String weather=from.weather()!=null?from.weather():to.weather();return new TourState(zones,lerp(from.enclosure(),to.enclosure(),alpha),lerp(from.rain(),to.rain(),alpha),lerp(from.wind(),to.wind(),alpha),lerp(from.storm(),to.storm(),alpha),weather);}

private float lerp(float a,float b,float alpha){return a+(b-a)*alpha;}}
