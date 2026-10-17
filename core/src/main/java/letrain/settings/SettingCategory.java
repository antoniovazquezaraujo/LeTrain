package letrain.settings;

/** Form section a setting belongs to (concept §3). */
public enum SettingCategory {
    TERRAIN("Terrain"), WORLD("Game"), ECONOMY("Economy"), CONSTRUCTION("Build"), UI("UI");

    private final String displayName;

    SettingCategory(String displayName) {
        this.displayName = displayName;
    }

    /** Section title shown by the settings/new-game UI (shared by 2D and 3D). */
    public String displayName() {
        return displayName;
    }
}
