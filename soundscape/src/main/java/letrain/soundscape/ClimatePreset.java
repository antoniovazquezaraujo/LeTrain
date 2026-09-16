package letrain.soundscape;

/**
 * A named weather preset: three intensities in the range 0.0-1.0. The game picks a preset and the
 * soundscape applies them; the names are free (clear, drizzle, storm...).
 */
public record ClimatePreset(String name,float rain,float wind,float storm){}
