package letrain.audio.material;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Logical identifier of a train sound material (ADR-029 §1).
 *
 * <p>
 * Canonical textual forms are {@code notch-<n>}, {@code trans-<from>-<to>}, {@code idle},
 * {@code start}, {@code stop}, {@code rolling}, {@code brakes} and {@code horn}. The profile
 * descriptor uses the equivalent dotted keys ({@code notch.1}, {@code trans.1-2}); both separators
 * are accepted by {@link #parse(String)}.
 *
 * <p>
 * The legacy aliases {@code wagons -> rolling} and {@code train-brakes -> brakes} (ADR-029 §5,
 * amendment 3) are normalized here, so descriptors and callers can keep using canonical English
 * ids while old material still resolves.
 */
public final class MaterialId {

    /** Role of a material in the train sound model. */
    public enum Role {
        NOTCH, TRANSITION, IDLE, START, STOP, ROLLING, BRAKES, HORN
    }

    private static final int MIN_NOTCH = 1;
    private static final int MAX_NOTCH = 10;
    private static final int MIN_TRANSITION_ENDPOINT = 0;
    private static final int MAX_TRANSITION_ENDPOINT = 10;

    private static final Map<String, Role> SINGLETON_ROLES = Map.of(
            "idle", Role.IDLE,
            "start", Role.START,
            "stop", Role.STOP,
            "rolling", Role.ROLLING,
            "brakes", Role.BRAKES,
            "horn", Role.HORN);

    private static final Set<Role> LOOP_ROLES = Set.of(
            Role.NOTCH, Role.IDLE, Role.ROLLING, Role.BRAKES);

    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "wagons", "rolling",
            "train-brakes", "brakes");

    private final Role role;
    private final int notch;
    private final int from;
    private final int to;
    private final String canonical;

    private MaterialId(Role role, int notch, int from, int to, String canonical) {
        this.role = role;
        this.notch = notch;
        this.from = from;
        this.to = to;
        this.canonical = canonical;
    }

    /** Creates a notch id ({@code notch-1}..{@code notch-10}). */
    public static MaterialId notch(int notch) {
        if (notch < MIN_NOTCH || notch > MAX_NOTCH) {
            throw new IllegalArgumentException(
                    "notch out of range [" + MIN_NOTCH + ".." + MAX_NOTCH + "]: " + notch);
        }
        return new MaterialId(Role.NOTCH, notch, -1, -1, "notch-" + notch);
    }

    /** Creates a directional transition id ({@code trans-<from>-<to>}). */
    public static MaterialId transition(int from, int to) {
        if (from < MIN_TRANSITION_ENDPOINT || from > MAX_TRANSITION_ENDPOINT
                || to < MIN_TRANSITION_ENDPOINT || to > MAX_TRANSITION_ENDPOINT) {
            throw new IllegalArgumentException("transition endpoints out of range ["
                    + MIN_TRANSITION_ENDPOINT + ".." + MAX_TRANSITION_ENDPOINT + "]: "
                    + from + " -> " + to);
        }
        if (from == to) {
            throw new IllegalArgumentException("transition endpoints must differ: " + from);
        }
        return new MaterialId(Role.TRANSITION, -1, from, to, "trans-" + from + "-" + to);
    }

    /** Creates a singleton-role id ({@code idle}, {@code rolling}, {@code brakes}, ...). */
    public static MaterialId of(Role role) {
        Objects.requireNonNull(role, "role must not be null");
        if (role == Role.NOTCH || role == Role.TRANSITION) {
            throw new IllegalArgumentException(role + " needs parameters; use notch()/transition()");
        }
        return new MaterialId(role, -1, -1, -1, role.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Parses a canonical id or a legacy alias. Returns {@link Optional#empty()} for unknown or
     * malformed tokens instead of throwing, because ids may come from descriptors or packs.
     */
    public static Optional<MaterialId> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        normalized = LEGACY_ALIASES.getOrDefault(normalized, normalized);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Role singleton = SINGLETON_ROLES.get(normalized);
        if (singleton != null) {
            return Optional.of(of(singleton));
        }

        if (normalized.startsWith("notch-") || normalized.startsWith("notch.")) {
            OptionalInt notch = parseNumber(normalized.substring("notch".length() + 1));
            if (notch.isPresent() && notch.getAsInt() >= MIN_NOTCH && notch.getAsInt() <= MAX_NOTCH) {
                return Optional.of(notch(notch.getAsInt()));
            }
            return Optional.empty();
        }

        if (normalized.startsWith("trans-") || normalized.startsWith("trans.")) {
            String pair = normalized.substring("trans".length() + 1);
            int dash = pair.indexOf('-');
            if (dash <= 0 || dash == pair.length() - 1) {
                return Optional.empty();
            }
            OptionalInt from = parseNumber(pair.substring(0, dash));
            OptionalInt to = parseNumber(pair.substring(dash + 1));
            if (from.isPresent() && to.isPresent() && from.getAsInt() != to.getAsInt()
                    && from.getAsInt() >= MIN_TRANSITION_ENDPOINT
                    && from.getAsInt() <= MAX_TRANSITION_ENDPOINT
                    && to.getAsInt() >= MIN_TRANSITION_ENDPOINT
                    && to.getAsInt() <= MAX_TRANSITION_ENDPOINT) {
                return Optional.of(transition(from.getAsInt(), to.getAsInt()));
            }
        }
        return Optional.empty();
    }

    private static OptionalInt parseNumber(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public Role role() {
        return role;
    }

    /** Notch number for {@link Role#NOTCH}, empty otherwise. */
    public OptionalInt notch() {
        return role == Role.NOTCH ? OptionalInt.of(notch) : OptionalInt.empty();
    }

    /** Origin notch for {@link Role#TRANSITION}, empty otherwise. */
    public OptionalInt from() {
        return role == Role.TRANSITION ? OptionalInt.of(from) : OptionalInt.empty();
    }

    /** Destination notch for {@link Role#TRANSITION}, empty otherwise. */
    public OptionalInt to() {
        return role == Role.TRANSITION ? OptionalInt.of(to) : OptionalInt.empty();
    }

    /** True for roles whose material must loop (ADR-029 §5). */
    public boolean isLoop() {
        return LOOP_ROLES.contains(role);
    }

    /** Canonical id, e.g. {@code notch-3} or {@code trans-2-5}. */
    public String canonical() {
        return canonical;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MaterialId id && canonical.equals(id.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    @Override
    public String toString() {
        return canonical;
    }
}
