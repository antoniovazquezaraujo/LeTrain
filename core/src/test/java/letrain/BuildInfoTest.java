package letrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BuildInfo: version comes from the Maven-filtered resource")
class BuildInfoTest {

    @Test
    @DisplayName("version is resolved (not the 'dev' fallback nor an unresolved token)")
    void version_isResolved() {
        String version = BuildInfo.version();

        assertFalse("dev".equals(version), "version.properties was not found on the classpath");
        assertFalse(version.contains("${"), "version.properties was not filtered: " + version);
    }

    @Test
    @DisplayName("versionTag has a single leading 'v'")
    void versionTag_hasSingleV() {
        String tag = BuildInfo.versionTag();

        assertTrue(tag.startsWith("v"), tag);
        assertFalse(tag.startsWith("vv"), tag);
    }
}
