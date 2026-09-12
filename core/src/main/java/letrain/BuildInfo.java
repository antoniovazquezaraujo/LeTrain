package letrain;

import java.io.InputStream;
import java.util.Properties;

/**
 * Application version, read from {@code version.properties} (Maven-filtered from the project
 * version in {@code pom.xml}) so the UI titles do not hardcode it.
 */
public final class BuildInfo {

    private static final String VERSION = load();

    private BuildInfo() {}

    /** The project version (e.g. {@code 1.0.0-beta.4-SNAPSHOT}), or {@code dev} if unavailable. */
    public static String version() {
        return VERSION;
    }

    /** The version with a single leading {@code v} (e.g. {@code v1.0.0-beta.4-SNAPSHOT}) for titles. */
    public static String versionTag() {
        if (VERSION.startsWith("v") || VERSION.startsWith("V")) {
            return VERSION;
        }
        return "v" + VERSION;
    }

    private static String load() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/version.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "dev");
        } catch (Exception e) {
            return "dev";
        }
    }
}
