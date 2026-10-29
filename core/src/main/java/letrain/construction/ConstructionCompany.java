package letrain.construction;

import java.util.Objects;
import java.util.Set;

/**
 * A construction company that can quote and, later, execute a planned route.
 *
 * <p>
 * The profile mixes the four decisions the owner wants visible together: price, time, quality and
 * structural capabilities. Quality is a single package (materials, labour and management) and has
 * no effect until maintenance exists; in phase 2 it is only a promise shown in the quote.
 *
 * <p>
 * Capabilities are independent of price/quality. A company that lacks {@link Capability#BRIDGE} or
 * {@link Capability#TUNNEL} cannot quote a route containing that structure.
 */
public record ConstructionCompany(String id, String name, double costFactor, double timeFactor,
        Quality quality, Set<Capability> capabilities) {

    /** Commercial construction quality; no mechanical effect until ADR-039 is implemented. */
    public enum Quality {
        LOW, HIGH
    }

    /** Structural work a company is allowed to quote. */
    public enum Capability {
        BRIDGE, TUNNEL
    }

    public ConstructionCompany {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!Double.isFinite(costFactor) || costFactor <= 0) {
            throw new IllegalArgumentException(
                    "costFactor must be positive, but was: " + costFactor);
        }
        if (!Double.isFinite(timeFactor) || timeFactor <= 0) {
            throw new IllegalArgumentException(
                    "timeFactor must be positive, but was: " + timeFactor);
        }
        quality = Objects.requireNonNull(quality, "quality must not be null");
        capabilities =
                Set.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    /** True when the company can quote every required structure. */
    public boolean supportsAll(Set<Capability> required) {
        return capabilities.containsAll(Objects.requireNonNull(required, "required"));
    }
}
