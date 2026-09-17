package letrain.soundscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed style file: the soundscape definition of a map. Everything is data: climate presets,
 * sound catalog, zones, presence curves, height sensitivities and weather sounds.
 */
public record SoundscapeStyle(Map<String,ClimatePreset>climatePresets,Map<String,SoundDef>sounds,Map<String,List<String>>zones,Map<String,List<Float>>presence,Map<String,Float>heightSensitivity,Map<String,List<Float>>climateSensitivity,Map<String,SoundDef>weatherSounds){

public SoundscapeStyle{climatePresets=immutable(climatePresets);sounds=immutable(sounds);zones=immutableListValues(zones);presence=immutableListValues(presence);heightSensitivity=immutable(heightSensitivity);climateSensitivity=immutableListValues(climateSensitivity);weatherSounds=immutable(weatherSounds);}

/** Sounds of a zone, or an empty list when the zone is unknown. */
public List<String>zoneSounds(String zone){return zones.getOrDefault(zone,List.of());}

/** Height sensitivity of a sound; 0 when the sound declares none. */
public float heightSensitivityOf(String sound){return heightSensitivity.getOrDefault(sound,0f);}

/** Climate sensitivities (rain, wind, storm) of a sound; zeros when the sound declares none. */
public List<Float>climateSensitivityOf(String sound){return climateSensitivity.getOrDefault(sound,List.of(0f,0f,0f));}

/** Presence curve (one value per band) of a sound; zeros when the sound declares none. */
public List<Float>presenceOf(String sound){List<Float>curve=presence.get(sound);return curve!=null?curve:zeroPresence();}

private static List<Float>zeroPresence(){return Collections.nCopies(Band.values().length,0f);}

private static<V>Map<String,V>immutable(Map<String,V>map){return Collections.unmodifiableMap(new LinkedHashMap<>(map));}

private static<V>Map<String,List<V>>immutableListValues(Map<String,List<V>>map){Map<String,List<V>>copy=new LinkedHashMap<>();map.forEach((key,value)->copy.put(key,List.copyOf(value)));return Collections.unmodifiableMap(copy);}}
