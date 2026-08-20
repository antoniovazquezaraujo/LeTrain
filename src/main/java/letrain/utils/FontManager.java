package letrain.utils;

import java.io.File;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-platform font loader that manages font loading from multiple sources.
 * Attempts to load from bundled assets first, then system fonts, with fallback
 * to default BitmapFont.
 */
public class FontManager {
    private static final Logger log = LoggerFactory.getLogger(FontManager.class);

    private FontManager() {
        // Utility class, no instantiation
    }

    /**
     * Loads a font by name and size with cross-platform fallback.
     *
     * @param fontName the name of the font (e.g., "Arial", "Consolas")
     * @param size     the font size in points
     * @return a BitmapFont instance, never null
     */
    public static BitmapFont loadFont(String fontName, int size) {
        ValidationUtils.requireNonEmpty(fontName, "fontName");
        ValidationUtils.requirePositive(size, "size");

        // First, try to load from bundled assets
        BitmapFont fontFromAssets = loadFontFromAssets(fontName, size);
        if (fontFromAssets != null) {
            return fontFromAssets;
        }

        // Second, try system fonts (Windows/Linux/macOS)
        BitmapFont fontFromSystem = loadFontFromSystem(fontName, size);
        if (fontFromSystem != null) {
            return fontFromSystem;
        }

        // Ultimate fallback: default BitmapFont with scaling
        return createScaledDefaultFont(size);
    }

    /**
     * Loads a monospace font for code editors with cross-platform fallback.
     *
     * @param size the font size in points
     * @return a monospace BitmapFont instance, never null
     */
    public static BitmapFont loadMonospaceFont(int size) {
        ValidationUtils.requirePositive(size, "size");

        // Try common monospace fonts in order, starting with our bundled JuliaMono
        String[] monospaceFonts = { "JuliaMono-Regular", "JetBrainsMono-Regular", "Inconsolata-Regular", "Consolas", "Courier New", "Menlo", "DejaVu Sans Mono" };

        for (String fontName : monospaceFonts) {
            BitmapFont font = loadFont(fontName, size);
            if (font != null) {
                font.setUseIntegerPositions(true); // Crisp rendering for code
                return font;
            }
        }

        // Fallback to default
        BitmapFont font = createScaledDefaultFont(size);
        font.setUseIntegerPositions(true);
        return font;
    }

    /**
     * Attempts to load a font from bundled assets (assets/fonts/).
     *
     * @param fontName the font name (e.g., "Arial")
     * @param size     the font size
     * @return the loaded font, or null if not found in assets
     */
    private static BitmapFont loadFontFromAssets(String fontName, int size) {
        try {
            // Try common font file extensions
            String[] extensions = { ".ttf", ".otf", ".woff", ".woff2" };
            for (String ext : extensions) {
                String path = "fonts/" + fontName + ext;
                log.debug("Checking for font asset: {}", path);
                if (Gdx.files.internal(path).exists()) {
                    log.info("Found font asset: {}", path);
                    return generateFontFromFile(Gdx.files.internal(path), size);
                }

                // Try lowercase variant
                path = "fonts/" + fontName.toLowerCase() + ext;
                log.debug("Checking for lowercase font asset: {}", path);
                if (Gdx.files.internal(path).exists()) {
                    log.info("Found lowercase font asset: {}", path);
                    return generateFontFromFile(Gdx.files.internal(path), size);
                }

                // Try without fonts/ prefix just in case
                path = fontName + ext;
                if (Gdx.files.internal(path).exists()) {
                    log.info("Found font asset (no prefix): {}", path);
                    return generateFontFromFile(Gdx.files.internal(path), size);
                }
            }
        } catch (Exception e) {
            log.error("Error loading font from assets (fontName={}): {}", fontName, e.getMessage());
        }
        return null;
    }

    /**
     * Attempts to load a font from system fonts directory.
     *
     * @param fontName the font name (e.g., "Arial")
     * @param size     the font size
     * @return the loaded font, or null if not found
     */
    private static BitmapFont loadFontFromSystem(String fontName, int size) {
        try {
            File systemFontFile = findSystemFont(fontName);
            if (systemFontFile != null && systemFontFile.exists()) {
                return generateFontFromFile(Gdx.files.absolute(systemFontFile.getAbsolutePath()), size);
            }
        } catch (Exception e) {
            // Silent fail, will return null
        }
        return null;
    }

    /**
     * Searches for a system font file across Windows, macOS, and Linux font
     * directories.
     *
     * @param fontName the font name to search for
     * @return the File if found, or null
     */
    private static File findSystemFont(String fontName) {
        String os = System.getProperty("os.name").toLowerCase();
        String[] searchDirs = new String[0];

        if (os.contains("win")) {
            // Windows font directories
            searchDirs = new String[] {
                    "C:\\Windows\\Fonts",
                    "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Microsoft\\Windows\\Fonts"
            };
        } else if (os.contains("mac")) {
            // macOS font directories
            searchDirs = new String[] {
                    "/Library/Fonts",
                    System.getProperty("user.home") + "/Library/Fonts",
                    "/System/Library/Fonts"
            };
        } else if (os.contains("nux") || os.contains("unix")) {
            // Linux font directories
            searchDirs = new String[] {
                    "/usr/share/fonts",
                    "/usr/local/share/fonts",
                    System.getProperty("user.home") + "/.fonts"
            };
        }

        // Search for font files
        String[] extensions = { ".ttf", ".otf" };
        for (String dir : searchDirs) {
            for (String ext : extensions) {
                // Try exact name
                File fontFile = new File(dir, fontName + ext);
                if (fontFile.exists()) {
                    return fontFile;
                }

                // Try split name parts (e.g., "Courier New" -> "CourierNew.ttf")
                String noSpaceName = fontName.replace(" ", "");
                fontFile = new File(dir, noSpaceName + ext);
                if (fontFile.exists()) {
                    return fontFile;
                }

                // Try lowercase
                fontFile = new File(dir, fontName.toLowerCase() + ext);
                if (fontFile.exists()) {
                    return fontFile;
                }
            }
        }

        return null;
    }

    /**
     * Generates a BitmapFont from a font file path using FreeTypeFontGenerator.
     *
     * @param fontFilePath the absolute path to the font file
     * @param size         the desired font size
     * @return a BitmapFont, or null if generation fails
     */
    private static BitmapFont generateFontFromFile(com.badlogic.gdx.files.FileHandle fileHandle, int size) {
        try {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fileHandle);
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = size;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.minFilter = Texture.TextureFilter.Linear;

            BitmapFont font = generator.generateFont(parameter);
            generator.dispose();
            return font;
        } catch (Exception e) {
            log.error("Error generating font from file {}: {}", fileHandle.path(), e.getMessage());
            return null;
        }
    }

    /**
     * Creates a default BitmapFont with appropriate scaling for the given size.
     * The default BitmapFont is small, so we scale it up relative to the requested
     * size.
     *
     * @param size the desired logical font size
     * @return a scaled BitmapFont
     */
    private static BitmapFont createScaledDefaultFont(int size) {
        BitmapFont font = new BitmapFont();
        // Default BitmapFont is approximately 14px, scale relative to requested size
        float scale = size / 14f;
        font.getData().setScale(scale);
        return font;
    }
}
