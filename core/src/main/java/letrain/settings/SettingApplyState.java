package letrain.settings;

/**
 * When a setting can be applied to a running game (concept §2). The settings UI uses this state to
 * decide whether a field is editable in-game, needs a confirmation, or is disabled with a reason.
 */
public enum SettingApplyState {
    /** Applies immediately (economy, UI, day length...). */
    LIVE,
    /** Can be recalculated, but the player deserves a warning (e.g. latitude → solar jump). */
    REAPPLY,
    /** Only takes effect when the world is generated (seed, terrain, industries). */
    NEW_GAME
}
