package letrain.audio.material;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import letrain.audio.synth.AudioSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared, static cache of resolved train materials (ADR-029 §6).
 *
 * <p>
 * The bank resolves and decodes each material at most once and exposes a typed API for the consumer
 * PRs: {@link #loop(int)}, {@link #transition(int, int)}, {@link #prefetch(MaterialId)} and
 * {@link #hasChain(int, int)}. It is the only owner of the shared cache, so every synthesizer
 * instance can reuse the same decoded samples.
 *
 * <p>
 * The shared instance uses the shipped {@code generic} profile and the classpath resolver; tests
 * and future pack integrations can build their own bank with any profile/resolver pair. Nothing
 * consumes the bank yet: this PR adds the material layer only (no behaviour change).
 */
public final class MaterialBank {

    private static final Logger log = LoggerFactory.getLogger(MaterialBank.class);

    /** Classpath location of the default descriptor (ADR-029 §5). */
    public static final String DEFAULT_PROFILE_RESOURCE = "/sound/profiles/generic.profile";

    private static volatile MaterialBank shared;

    private final MaterialProfile profile;
    private final MaterialResolver resolver;
    private final Map<MaterialId, Optional<AudioSample>> cache = new ConcurrentHashMap<>();

    public MaterialBank(MaterialProfile profile, MaterialResolver resolver) {
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /** Lazily created shared bank (default profile + classpath resolver). */
    public static MaterialBank shared() {
        MaterialBank local = shared;
        if (local == null) {
            synchronized (MaterialBank.class) {
                local = shared;
                if (local == null) {
                    MaterialProfile profile = loadDefaultProfile();
                    local = new MaterialBank(profile, new ClasspathMaterialResolver(profile));
                    shared = local;
                }
            }
        }
        return local;
    }

    /** Notch loop material ({@code notch-1}..{@code notch-10}). */
    public Optional<AudioSample> loop(int notch) {
        return resolve(MaterialId.notch(notch));
    }

    /** Directional transition material ({@code trans-<from>-<to>}). */
    public Optional<AudioSample> transition(int from, int to) {
        return resolve(MaterialId.transition(from, to));
    }

    /** Profile metadata of a material, without decoding it. */
    public Optional<MaterialRef> material(MaterialId id) {
        return profile.material(id);
    }

    /** Decodes {@code id} into the cache if it was not resolved yet. */
    public void prefetch(MaterialId id) {
        resolve(id);
    }

    /** Convenience prefetch for a notch loop. */
    public void prefetchLoop(int notch) {
        resolve(MaterialId.notch(notch));
    }

    /** Convenience prefetch for a transition. */
    public void prefetchTransition(int from, int to) {
        resolve(MaterialId.transition(from, to));
    }

    /**
     * True when every adjacent step between {@code from} and {@code to} is declared in the profile,
     * i.e. the simple-step chain of ADR-029 §3 level 2 can be built. A chain of zero steps
     * ({@code from == to}) is trivially complete.
     */
    public boolean hasChain(int from, int to) {
        if (from == to) {
            return true;
        }
        int step = from < to ? 1 : -1;
        for (int notch = from; notch != to; notch += step) {
            if (!profile.has(MaterialId.transition(notch, notch + step))) {
                return false;
            }
        }
        return true;
    }

    public MaterialProfile profile() {
        return profile;
    }

    private Optional<AudioSample> resolve(MaterialId id) {
        return cache.computeIfAbsent(id, key -> {
            Optional<AudioSample> resolved = resolver.resolve(key);
            return resolved == null ? Optional.empty() : resolved;
        });
    }

    static MaterialProfile loadDefaultProfile() {
        try (InputStream in = MaterialBank.class.getResourceAsStream(DEFAULT_PROFILE_RESOURCE)) {
            if (in == null) {
                log.warn("profile not found on classpath: {}", DEFAULT_PROFILE_RESOURCE);
                return MaterialProfile.empty(MaterialProfileParser.DEFAULT_PROFILE_NAME);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return MaterialProfileParser.parse(reader);
            }
        } catch (IOException e) {
            log.warn("cannot read {}: {}", DEFAULT_PROFILE_RESOURCE, e.getMessage());
            return MaterialProfile.empty(MaterialProfileParser.DEFAULT_PROFILE_NAME);
        }
    }
}
