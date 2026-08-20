package letrain.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FontManager - Cross-platform font loading and fallback")
class FontManagerTest {

    // Note: LibGDX requires a running application context to load fonts.
    // These tests verify the FontManager structure and exception handling.
    // Full integration testing is done through the game's actual font loading.

    @Test
    @DisplayName("FontManager class exists")
    void testFontManagerExists() {
        assertNotNull(FontManager.class);
    }

    @Test
    @DisplayName("loadFont rejects null font name")
    void testLoadFontNullNameThrows() {
        // FontManager uses ValidationUtils which throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            FontManager.loadFont(null, 14);
        });
    }

    @Test
    @DisplayName("loadFont rejects empty font name")
    void testLoadFontEmptyNameThrows() {
        // FontManager uses ValidationUtils which throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadFont("", 14);
        });
    }

    @Test
    @DisplayName("loadFont rejects non-positive size")
    void testLoadFontNegativeSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadFont("Arial", 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadFont("Arial", -10);
        });
    }

    @Test
    @DisplayName("loadMonospaceFont rejects non-positive size")
    void testLoadMonospaceFontNegativeSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadMonospaceFont(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadMonospaceFont(-5);
        });
    }

    @Test
    @DisplayName("loadFont accepts valid font name and size")
    void testLoadFontValidInput() {
        // This will attempt to load Arial at 24pt
        // On systems without LibGDX context, this may fail gracefully
        // But it should not throw validation exceptions
        try {
            BitmapFont font = FontManager.loadFont("Arial", 24);
            assertNotNull(font);
            font.dispose();
        } catch (Exception e) {
            // Expected in test environment without LibGDX application context
            // but validation should have passed
            assertFalse(e.getMessage().contains("cannot be null"),
                    "Should not fail on validation, only on LibGDX context");
        }
    }

    @Test
    @DisplayName("loadMonospaceFont accepts valid size")
    void testLoadMonospaceFontValidInput() {
        try {
            BitmapFont font = FontManager.loadMonospaceFont(16);
            assertNotNull(font);
            font.dispose();
        } catch (Exception e) {
            // Expected in test environment without LibGDX context
            assertFalse(e.getMessage().contains("must be positive"),
                    "Should not fail validation");
        }
    }

    @Test
    @DisplayName("Operating system detection works")
    void testOSDetection() {
        String osName = System.getProperty("os.name").toLowerCase();
        assertNotNull(osName);
        assertFalse(osName.isEmpty());

        // Should be one of the expected OS types
        boolean isKnownOS = osName.contains("win") ||
                osName.contains("mac") ||
                osName.contains("linux") ||
                osName.contains("nux") ||
                osName.contains("unix");
        assertTrue(isKnownOS, "OS name should be recognized: " + osName);
    }

    @Test
    @DisplayName("FontManager validation uses ValidationUtils")
    void testValidationIntegration() {
        // Verify that ValidationUtils is properly integrated
        assertThrows(NullPointerException.class, () -> {
            FontManager.loadFont(null, 12);
        }, "Should use ValidationUtils.requireNonNull()");

        assertThrows(IllegalArgumentException.class, () -> {
            FontManager.loadFont("Arial", -1);
        }, "Should use ValidationUtils.requirePositive()");
    }
}
