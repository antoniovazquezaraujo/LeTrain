package letrain.weather;

/** Current intensities (0..1) of the ambient weather. */
public record WeatherState(float rain,float wind,float storm){

public static final WeatherState CLEAR=new WeatherState(0f,0f,0f);}
