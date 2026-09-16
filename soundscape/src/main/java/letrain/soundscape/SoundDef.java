package letrain.soundscape;

import java.util.List;

/**
 * A single sound entry of the catalog: a name and one or more material paths (wildcards allowed,
 * e.g. {@code sea/waves-*.wav}). All sounds are loops; event-like sounds carry their gaps inside
 * longer takes, so no playback mode is declared.
 */
public record SoundDef(String name,List<String>material){

public SoundDef{material=List.copyOf(material);}}
