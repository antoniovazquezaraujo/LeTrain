package letrain.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder tokens used by the DSL quick reference (#828): {@code <station>}, {@code <speed>},
 * etc. They are the only "template holes" a reference snippet may carry, so both editors can find,
 * select and replace them consistently.
 *
 * <p>
 * Syntax: {@code [<]([a-z]+)[>]}. Only the {@link #TOKENS allowed tokens} are meaningful; a
 * well-formed but unknown lowercase token still matches {@link #find(String)} so callers can flag
 * it (see {@link #isAllowedToken(String)}).
 */
public final class Placeholders {

    /** Tokens the quick reference may use. Keep in sync with the PROGRAM tree (#828). */
    public static final Set<String> TOKENS = Set.of("station", "sensor", "train", "fork",
            "semaphore", "signal", "speed", "limit", "count", "name", "time", "dir");

    private static final Pattern PATTERN = Pattern.compile("[<]([a-z]+)[>]");

    private Placeholders() {}

    /**
     * Start (inclusive) and end (exclusive) of every placeholder in {@code text}, in reading order.
     * The returned arrays are freshly allocated {@code int[2]}.
     */
    public static List<int[]> find(String text) {
        List<int[]> spans = new ArrayList<>();
        if (text == null) {
            return spans;
        }
        Matcher matcher = PATTERN.matcher(text);
        while (matcher.find()) {
            spans.add(new int[] {matcher.start(), matcher.end()});
        }
        return spans;
    }

    /** Token name of a span returned by {@link #find(String)} (e.g. {@code "station"}). */
    public static String tokenAt(String text, int[] span) {
        return text.substring(span[0] + 1, span[1] - 1);
    }

    /** True when {@code token} is one of the {@link #TOKENS allowed tokens}. */
    public static boolean isAllowedToken(String token) {
        return token != null && TOKENS.contains(token);
    }
}
