package letrain.construction;

/**
 * Read-only query a UI can use to lock manual editing while automatic construction is running
 * (ADR-037).
 *
 * <p>
 * The interface keeps the future presenter decoupled from the queue implementation and avoids
 * putting construction state on the global {@code Model}.
 */
@FunctionalInterface
public interface ConstructionGuard {

    /** True while a route construction job is actively building cells. */
    boolean isEditingLocked();
}
