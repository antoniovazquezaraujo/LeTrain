package letrain.audio.material;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable sound profile: profile name, pack version and the materials declared by a descriptor
 * like {@code sound/profiles/generic.profile} (ADR-029 §5).
 */
public final class MaterialProfile {

    private final String name;
    private final int packVersion;
    private final Map<MaterialId, MaterialRef> materials;

    public MaterialProfile(String name, int packVersion, Map<MaterialId, MaterialRef> materials) {
        this.name = name == null ? "" : name.trim();
        this.packVersion = packVersion;
        this.materials = Map.copyOf(Objects.requireNonNull(materials, "materials must not be null"));
    }

    /** Empty profile used as fallback when a descriptor is missing or unreadable. */
    public static MaterialProfile empty(String name) {
        return new MaterialProfile(name, 0, Map.of());
    }

    public String name() {
        return name;
    }

    public int packVersion() {
        return packVersion;
    }

    /** Metadata for a material, empty when the profile does not declare it. */
    public Optional<MaterialRef> material(MaterialId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(materials.get(id));
    }

    public boolean has(MaterialId id) {
        return material(id).isPresent();
    }

    /** All declared materials, keyed by canonical id. */
    public Map<MaterialId, MaterialRef> materials() {
        return materials;
    }

    public int size() {
        return materials.size();
    }
}
