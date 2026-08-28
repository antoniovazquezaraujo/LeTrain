package letrain.utils;

import java.util.Objects;

/**
 * Centralized validation utility for null safety and input validation.
 * Provides wrapper methods around Objects.requireNonNull() with descriptive
 * error messages.
 */
public class ValidationUtils {

    private ValidationUtils() {
        // Utility class, no instantiation
    }

    /**
     * Validates that the given value is not null.
     *
     * @param <T>       the type of the object to validate
     * @param value     the value to check
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if non-null
     * @throws NullPointerException if value is null with descriptive message
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }

    /**
     * Validates that the given integer is positive (> 0).
     *
     * @param value     the value to check
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if positive
     * @throws IllegalArgumentException if value is not positive
     */
    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, but was: " + value);
        }
        return value;
    }

    /**
     * Validates that the given integer is non-negative (>= 0).
     *
     * @param value     the value to check
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if non-negative
     * @throws IllegalArgumentException if value is negative
     */
    public static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative, but was: " + value);
        }
        return value;
    }

    /**
     * Validates that the given long is positive (> 0).
     *
     * @param value     the value to check
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if positive
     * @throws IllegalArgumentException if value is not positive
     */
    public static long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, but was: " + value);
        }
        return value;
    }

    /**
     * Validates that the given double is positive (> 0).
     *
     * @param value     the value to check
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if positive
     * @throws IllegalArgumentException if value is not positive or NaN
     */
    public static double requirePositive(double value, String fieldName) {
        if (Double.isNaN(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive and not NaN, but was: " + value);
        }
        return value;
    }

    /**
     * Validates that the given string is not null and not empty.
     *
     * @param value     the string to validate
     * @param fieldName the name of the field being validated (for error message)
     * @return the value if non-null and non-empty
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is empty
     */
    public static String requireNonEmpty(String value, String fieldName) {
        requireNonNull(value, fieldName);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return value;
    }

    /**
     * Validates that a condition is true, throwing IllegalArgumentException if
     * false.
     *
     * @param condition the boolean condition to check
     * @param message   the error message if condition is false
     * @throws IllegalArgumentException if condition is false
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validates that a condition is true, throwing IllegalStateException if false.
     * Useful for state validation (vs input validation).
     *
     * @param condition the boolean condition to check
     * @param message   the error message if condition is false
     * @throws IllegalStateException if condition is false
     */
    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
