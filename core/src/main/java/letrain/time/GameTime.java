package letrain.time;

/** Immutable game date and time: day (1-based), hour and minute. */
public record GameTime(int day,int hour,int minute)implements Comparable<GameTime>{

public GameTime{day=Math.max(1,day);hour=Math.floorMod(hour,24);minute=Math.floorMod(minute,60);}

public int minuteOfDay(){return hour*60+minute;}

@Override public int compareTo(GameTime other){if(day!=other.day){return Integer.compare(day,other.day);}return Integer.compare(minuteOfDay(),other.minuteOfDay());}

@Override public String toString(){return String.format("Día %d %02d:%02d",day,hour,minute);}}
