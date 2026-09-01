package letrain.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SerializationHelper - Transient field reinitialization")
class SerializationHelperTest {

    @Test
    @DisplayName("ensureListInitialized - returns original list if non-null")
    void testEnsureListInitialized_NonNullList() {
        List<String> original = new ArrayList<>();
        original.add("element");

        List<String> result = SerializationHelper.ensureListInitialized(original);

        assertSame(original, result);
        assertEquals(1, result.size());
        assertEquals("element", result.get(0));
    }

    @Test
    @DisplayName("ensureListInitialized - creates ArrayList if null")
    void testEnsureListInitialized_NullList() {
        List<String> result = SerializationHelper.ensureListInitialized(null);

        assertNotNull(result);
        assertTrue(result instanceof ArrayList);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("ensureListInitialized - returned ArrayList is mutable")
    void testEnsureListInitialized_Mutable() {
        List<Integer> result = SerializationHelper.ensureListInitialized(null);

        result.add(42);
        result.add(100);

        assertEquals(2, result.size());
        assertEquals(42, result.get(0));
        assertEquals(100, result.get(1));
    }

    @Test
    @DisplayName("ensureListInitializedConcurrent - returns original list if non-null")
    void testEnsureListInitializedConcurrent_NonNullList() {
        List<String> original = new CopyOnWriteArrayList<>();
        original.add("concurrent");

        List<String> result = SerializationHelper.ensureListInitializedConcurrent(original);

        assertSame(original, result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("ensureListInitializedConcurrent - creates CopyOnWriteArrayList if null")
    void testEnsureListInitializedConcurrent_NullList() {
        List<String> result = SerializationHelper.ensureListInitializedConcurrent(null);

        assertNotNull(result);
        assertTrue(result instanceof CopyOnWriteArrayList);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("ensureListInitializedConcurrent - returned list is thread-safe")
    void testEnsureListInitializedConcurrent_ThreadSafe() {
        List<Integer> result = SerializationHelper.ensureListInitializedConcurrent(null);

        // CopyOnWriteArrayList should handle concurrent operations
        result.add(1);
        result.add(2);
        result.add(3);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("reinitializeTransientList - delegates to ensureListInitialized")
    void testReinitializeTransientList() {
        List<String> result = SerializationHelper.reinitializeTransientList(null);

        assertNotNull(result);
        assertTrue(result instanceof ArrayList);
    }

    @Test
    @DisplayName("reinitializeTransientListConcurrent - delegates to ensureListInitializedConcurrent")
    void testReinitializeTransientListConcurrent() {
        List<String> result = SerializationHelper.reinitializeTransientListConcurrent(null);

        assertNotNull(result);
        assertTrue(result instanceof CopyOnWriteArrayList);
    }

    @Test
    @DisplayName("ensureListInitialized - preserves generic type information")
    void testEnsureListInitialized_GenericType() {
        List<Long> nullList = null;
        List<Long> result = SerializationHelper.ensureListInitialized(nullList);

        result.add(123456789L);
        assertEquals(123456789L, result.get(0));
    }
}
