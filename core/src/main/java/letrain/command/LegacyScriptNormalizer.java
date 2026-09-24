package letrain.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes legacy (pre-ADR-022) automation text to the comma waypoint syntax. Since timetables
 * made commas mandatory between a waypoint's actions, text written with the old syntax
 * ({@code add station 2 reverse unload}, {@code SPEED 0 WAIT 3 SPEED 3}) would no longer parse.
 *
 * <p>
 * Adopted compatibility policy: <b>strict when writing, tolerant when loading existing text from
 * disk</b>. Loading surfaces (saved games, scenario files and their recorded journals) run their
 * text through this class, which only rewrites lines it fully understands; anything else is left
 * untouched so the strict parser reports the error. The rewrite is deterministic and idempotent:
 * lines that already use commas, times ({@code 10:23}) or unknown tokens are returned unchanged.
 */
public final class LegacyScriptNormalizer {

    private static final Pattern WAYPOINT_LINE =
            Pattern.compile("^(\\s*add\\s+(?:station|sensor)\\s+)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCE = Pattern.compile("-?\\d+|\"[^\"]*\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final Set<String> DIRECTIONS =
            Set.of("n", "ne", "e", "se", "s", "sw", "w", "nw");
    private static final Set<String> SIMPLE_ACTIONS = Set.of("load", "unload", "reverse", "stop");

    private LegacyScriptNormalizer() {}

    /**
     * Returns {@code text} with legacy waypoint lines rewritten to the comma syntax. The same
     * instance is returned (same reference) when there is nothing to normalize.
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            String normalized = normalizeLine(lines[i]);
            changed |= !normalized.equals(lines[i]);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(normalized);
        }
        return changed ? sb.toString() : text;
    }

    /**
     * Rewrites a single {@code add station}/{@code add sensor} line with two or more comma-less
     * actions. Returns the line unchanged when it is not a waypoint line, already uses commas or
     * times, or contains anything this normalizer does not fully understand.
     */
    static String normalizeLine(String line) {
        Matcher matcher = WAYPOINT_LINE.matcher(line);
        if (!matcher.matches()) {
            return line;
        }
        String head = matcher.group(1);
        String tail = matcher.group(2);
        if (tail.indexOf(',') >= 0 || tail.indexOf(':') >= 0) {
            return line; // already comma syntax, or a time attribute
        }

        String body = tail.stripTrailing();
        String suffix = "";
        while (!body.isEmpty() && (body.endsWith(";") || body.endsWith("}"))) {
            suffix = body.charAt(body.length() - 1) + suffix;
            body = body.substring(0, body.length() - 1).stripTrailing();
        }

        List<String> tokens = tokenize(body);
        if (tokens.isEmpty() || !REFERENCE.matcher(tokens.get(0)).matches()) {
            return line;
        }
        int next = 1;
        String direction = null;
        if (tokens.size() > next
                && DIRECTIONS.contains(tokens.get(next).toLowerCase(Locale.ROOT))) {
            direction = tokens.get(next).toLowerCase(Locale.ROOT);
            next++;
        }

        List<String> actions = new ArrayList<>();
        while (next < tokens.size()) {
            String token = tokens.get(next).toLowerCase(Locale.ROOT);
            if (SIMPLE_ACTIONS.contains(token)) {
                actions.add(token);
                next++;
            } else if ("wait".equals(token) || "speed".equals(token)) {
                if (next + 1 >= tokens.size() || !NUMBER.matcher(tokens.get(next + 1)).matches()) {
                    return line; // malformed parameter: let the strict parser report it
                }
                actions.add(token + " " + tokens.get(next + 1));
                next += 2;
            } else {
                return line; // unknown token: leave the line for the strict parser
            }
        }
        if (actions.size() <= 1) {
            return line; // nothing to separate
        }

        StringBuilder sb = new StringBuilder(head).append(tokens.get(0));
        if (direction != null) {
            sb.append(' ').append(direction);
        }
        for (int i = 0; i < actions.size(); i++) {
            sb.append(i == 0 ? ' ' : ", ").append(actions.get(i));
        }
        if (suffix.startsWith("}")) {
            sb.append(' ');
        }
        return sb.append(suffix).toString();
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int end = text.indexOf('"', i + 1);
                if (end < 0) {
                    end = text.length() - 1;
                }
                tokens.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                int start = i;
                while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                tokens.add(text.substring(start, i));
            }
        }
        return tokens;
    }
}
