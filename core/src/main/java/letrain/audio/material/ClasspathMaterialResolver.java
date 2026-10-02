package letrain.audio.material;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import javax.sound.sampled.UnsupportedAudioFileException;
import letrain.audio.synth.AudioSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MaterialResolver} that reads the material from the application classpath, following the
 * classpath paths declared by a {@link MaterialProfile} (ADR-029 §5).
 *
 * <p>
 * Failures never propagate: a missing resource or an undecodable file is logged as {@code WARN} and
 * resolves to {@link Optional#empty()}, so callers can apply the §3 fallback chain.
 */
public final class ClasspathMaterialResolver implements MaterialResolver {

    private static final Logger log = LoggerFactory.getLogger(ClasspathMaterialResolver.class);

    private final MaterialProfile profile;

    public ClasspathMaterialResolver(MaterialProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    @Override
    public Optional<AudioSample> resolve(MaterialId id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<MaterialRef> ref = profile.material(id);
        if (ref.isEmpty()) {
            log.warn("material '{}' is not declared in profile '{}'", id, profile.name());
            return Optional.empty();
        }
        String path = ref.get().path();
        URL url = ClasspathMaterialResolver.class.getResource(toClasspathPath(path));
        if (url == null) {
            log.warn("material '{}' not found on classpath: {}", id, path);
            return Optional.empty();
        }
        try {
            return Optional.of(new AudioSample(url));
        } catch (IOException | UnsupportedAudioFileException e) {
            log.warn("cannot decode material '{}' from {}: {}", id, path, e.getMessage());
            return Optional.empty();
        }
    }

    private static String toClasspathPath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
