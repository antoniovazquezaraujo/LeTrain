package letrain.settings;

/** Value grammar of a setting, used by the UI to pick a widget and by validation to parse it. */
public enum SettingValueType {
    INTEGER, DECIMAL, BOOLEAN,
    /** Integer seed; the literal {@code random} asks the engine for a fresh one (D3). */
    SEED,
    /** Time of day {@code H:MM}/{@code HH:MM}, 00:00..23:59; normalised to zero-padded HH:MM. */
    TIME_OF_DAY
}
