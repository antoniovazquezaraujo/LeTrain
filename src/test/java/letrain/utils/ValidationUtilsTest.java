package letrain.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValidationUtils - Null safety and input validation")
class ValidationUtilsTest {

    @Test
    @DisplayName("requireNonNull - accepts non-null value")
    void testRequireNonNull_Valid() {
        String value = "test";
        String result = ValidationUtils.requireNonNull(value, "testField");
        assertEquals("test", result);
    }

    @Test
    @DisplayName("requireNonNull - throws NPE for null value")
    void testRequireNonNull_InvalidNull() {
        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> ValidationUtils.requireNonNull(null, "testField"));
        assertEquals("testField must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("requirePositive - accepts positive integer")
    void testRequirePositive_Int_Valid() {
        int result = ValidationUtils.requirePositive(42, "count");
        assertEquals(42, result);
    }

    @Test
    @DisplayName("requirePositive - rejects zero")
    void testRequirePositive_Int_InvalidZero() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requirePositive(0, "count"));
        assertEquals("count must be positive, but was: 0", ex.getMessage());
    }

    @Test
    @DisplayName("requirePositive - rejects negative integer")
    void testRequirePositive_Int_InvalidNegative() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requirePositive(-10, "count"));
        assertEquals("count must be positive, but was: -10", ex.getMessage());
    }

    @Test
    @DisplayName("requireNonNegative - accepts zero")
    void testRequireNonNegative_ValidZero() {
        int result = ValidationUtils.requireNonNegative(0, "offset");
        assertEquals(0, result);
    }

    @Test
    @DisplayName("requireNonNegative - accepts positive")
    void testRequireNonNegative_ValidPositive() {
        int result = ValidationUtils.requireNonNegative(100, "offset");
        assertEquals(100, result);
    }

    @Test
    @DisplayName("requireNonNegative - rejects negative")
    void testRequireNonNegative_InvalidNegative() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNonNegative(-1, "offset"));
        assertEquals("offset must be non-negative, but was: -1", ex.getMessage());
    }

    @Test
    @DisplayName("requirePositive long - accepts positive value")
    void testRequirePositive_Long_Valid() {
        long result = ValidationUtils.requirePositive(999999999L, "bigNumber");
        assertEquals(999999999L, result);
    }

    @Test
    @DisplayName("requirePositive double - accepts positive value")
    void testRequirePositive_Double_Valid() {
        double result = ValidationUtils.requirePositive(3.14, "pi");
        assertEquals(3.14, result);
    }

    @Test
    @DisplayName("requirePositive double - rejects NaN")
    void testRequirePositive_Double_InvalidNaN() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ValidationUtils.requirePositive(Double.NaN, "value"));
        assertTrue(ex.getMessage().contains("must be positive and not NaN"));
    }

    @Test
    @DisplayName("requireNonEmpty - accepts non-empty string")
    void testRequireNonEmpty_Valid() {
        String result = ValidationUtils.requireNonEmpty("hello", "message");
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("requireNonEmpty - rejects empty string")
    void testRequireNonEmpty_InvalidEmpty() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNonEmpty("", "message"));
        assertEquals("message must not be empty", ex.getMessage());
    }

    @Test
    @DisplayName("requireNonEmpty - rejects null string")
    void testRequireNonEmpty_InvalidNull() {
        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> ValidationUtils.requireNonEmpty(null, "message"));
        assertEquals("message must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("require - condition true does not throw")
    void testRequire_Valid() {
        // Should not throw
        ValidationUtils.require(true, "Condition must be true");
    }

    @Test
    @DisplayName("require - condition false throws IAE")
    void testRequire_Invalid() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ValidationUtils.require(false, "Custom error message"));
        assertEquals("Custom error message", ex.getMessage());
    }

    @Test
    @DisplayName("requireState - condition true does not throw")
    void testRequireState_Valid() {
        // Should not throw
        ValidationUtils.requireState(true, "State must be valid");
    }

    @Test
    @DisplayName("requireState - condition false throws ISE")
    void testRequireState_Invalid() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> ValidationUtils.requireState(false, "Invalid state error"));
        assertEquals("Invalid state error", ex.getMessage());
    }
}
