package letrain.weather;

import java.util.List;

/**
 * The climate calendar of a world (ADR-027): user-defined day ranges with probability tables for
 * rain, wind and storm. Days not covered by any range use the default probabilities.
 */
public record ClimateCalendar(List<SeasonRange>ranges,WeatherProbabilities fallback){

public ClimateCalendar{if(ranges==null){throw new IllegalArgumentException("ranges must not be null");}ranges=List.copyOf(ranges);fallback=fallback==null?new WeatherProbabilities(0f,0f,0f):fallback;validate(ranges);}

public static ClimateCalendar empty(){return new ClimateCalendar(List.of(),new WeatherProbabilities(0f,0f,0f));}

public WeatherProbabilities probabilitiesOn(int dayOfYear){for(SeasonRange range:ranges){if(range.contains(dayOfYear)){return range.probabilities();}}return fallback;}

private static void validate(List<SeasonRange>ranges){for(int i=0;i<ranges.size();i++){for(int j=i+1;j<ranges.size();j++){if(overlaps(ranges.get(i),ranges.get(j))){throw new IllegalArgumentException("season ranges overlap: "+ranges.get(i)+" and "+ranges.get(j));}}}}

private static boolean overlaps(SeasonRange left,SeasonRange right){for(int day=1;day<=366;day++){if(left.contains(day)&&right.contains(day)){return true;}}return false;}}
