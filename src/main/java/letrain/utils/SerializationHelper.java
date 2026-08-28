package letrain.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class for managing transient fields during
 * serialization/deserialization.
 * <p>
 * When a class with transient fields is deserialized, the transient fields are
 * not
 * restored by default (they remain null). This utility provides convenient
 * methods
 * to reinitialize transient fields in the {@code readObject()} method.
 * </p>
 */
public class SerializationHelper {

    private SerializationHelper() {
        // Utility class, no instantiation
    }

    /**
     * Ensures that a collection field is initialized after deserialization.
     * <p>
     * If the list is null (which happens for transient fields after
     * deserialization),
     * creates and returns a new ArrayList. Otherwise returns the original list.
     * </p>
     *
     * @param <T>  the type of elements in the list
     * @param list the list to check, may be null
     * @return the original list if non-null, or a new ArrayList if null
     */
    public static <T> List<T> ensureListInitialized(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    /**
     * Ensures that a thread-safe collection field is initialized after
     * deserialization.
     * <p>
     * If the list is null (which happens for transient fields after
     * deserialization),
     * creates and returns a new CopyOnWriteArrayList (suitable for concurrent
     * access).
     * Otherwise returns the original list.
     * </p>
     * <p>
     * Use this for transient fields that may be accessed from multiple threads,
     * such as listener collections.
     * </p>
     *
     * @param <T>  the type of elements in the list
     * @param list the list to check, may be null
     * @return the original list if non-null, or a new CopyOnWriteArrayList if null
     */
    public static <T> List<T> ensureListInitializedConcurrent(List<T> list) {
        return list != null ? list : new CopyOnWriteArrayList<>();
    }

    /**
     * Reinitializes a transient list field after deserialization.
     * <p>
     * This is a convenience method for use in {@code readObject()} when you have
     * a transient list field. It ensures the field is never null after
     * deserialization,
     * using a standard ArrayList.
     * </p>
     * <p>
     * Example usage in readObject():
     *
     * <pre>
     * private void readObject(ObjectInputStream ois)
     *         throws IOException, ClassNotFoundException {
     *     ois.defaultReadObject();
     *     myListField = SerializationHelper.ensureListInitialized(myListField);
     * }
     * </pre>
     *
     * @param <T>  the type of elements in the list
     * @param list the transient list to reinitialize
     * @return the list if non-null, or new ArrayList if null
     */
    public static <T> List<T> reinitializeTransientList(List<T> list) {
        return ensureListInitialized(list);
    }

    /**
     * Reinitializes a transient thread-safe list field after deserialization.
     * <p>
     * This is a convenience method for use in {@code readObject()} when you have
     * a transient list field that will be accessed concurrently. It ensures the
     * field
     * is never null after deserialization, using a CopyOnWriteArrayList.
     * </p>
     * <p>
     * Example usage in readObject():
     *
     * <pre>
     * private void readObject(ObjectInputStream ois)
     *         throws IOException, ClassNotFoundException {
     *     ois.defaultReadObject();
     *     listeners = SerializationHelper.ensureListInitializedConcurrent(listeners);
     * }
     * </pre>
     *
     * @param <T>  the type of elements in the list
     * @param list the transient list to reinitialize
     * @return the list if non-null, or new CopyOnWriteArrayList if null
     */
    public static <T> List<T> reinitializeTransientListConcurrent(List<T> list) {
        return ensureListInitializedConcurrent(list);
    }
}
