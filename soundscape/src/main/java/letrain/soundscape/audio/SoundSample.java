package letrain.soundscape.audio;

/** A loaded mono sample in the range -1.0 .. 1.0, ready for mixing. */
public record SoundSample(String name,float[]data,float sampleRate){

public SoundSample{if(data==null||data.length==0){throw new IllegalArgumentException("sample data must not be empty");}}

public int length(){return data.length;}

public float frame(int index){return data[index];}}
