package letrain.settings;

/**
 * State one settings field shows in the in-game Settings dialog (S3b, #764), shared by both clients
 * so the 2D terminal and the 3D HUD cannot drift apart. It folds the catalogue's
 * {@link SettingApplyState} with the scenario lock (D10): a scenario-fixed key is {@link #SCENARIO}
 * whatever the catalogue says.
 *
 * <p>
 * {@link #LIVE} and {@link #REAPPLY} are editable (REAPPLY asks for a confirmation before
 * applying); {@link #NEW_GAME} is read-only in-game and {@link #SCENARIO} is locked by the scenario
 * and has no colour or legend entry of its own (#767).
 */
public enum SettingFieldState {
    /** Applies immediately (economy, UI, day length...). */
    LIVE,
    /** Can be recalculated, but the player deserves a warning (e.g. latitude). */
    REAPPLY,
    /** Only takes effect when the world is generated: read-only in-game. */
    NEW_GAME,
    /** Fixed by the scenario's {@code configuration { }} block: locked, no colour of its own. */
    SCENARIO;

    /** Whether the field can be edited in-game (LIVE and REAPPLY only). */
    public boolean editable() {
        return this == LIVE || this == REAPPLY;
    }
}
