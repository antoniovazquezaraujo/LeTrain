package letrain.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure text-editing support shared by the 2D and 3D program editors (#828): insertion with the
 * current line indentation, placeholder spans, newline indentation, dedent and brace-depth
 * detection. No Lanterna or libGDX dependency, so it is unit-tested without opening a UI.
 */
public final class ProgramTextEdit {

    /** Start (inclusive) and end (exclusive) of a placeholder token in the editor text. */
    public record Span(int start, int end) {
        public int length() {
            return end - start;
        }
    }

    /** Result of inserting a snippet: the new text, the caret offset and its placeholders. */
    public record Insertion(String text, int caret, List<Span> placeholders) {
    }

    private ProgramTextEdit() {}

    /**
     * Inserts {@code snippet} at the caret's line. A blank line receives the snippet in place; a
     * line with content receives it on a new line just below, preserving that line's indentation.
     * Every snippet line is prefixed with the base indentation.
     */
    public static Insertion insert(String text, int caret, String snippet) {
        String source = text == null ? "" : text;
        int at = Math.max(0, Math.min(caret, source.length()));
        String template = normalise(snippet);
        if (template.isEmpty()) {
            return new Insertion(source, at, List.of());
        }

        int lineStart = lineStart(source, at);
        int lineEnd = lineEnd(source, at);
        String line = source.substring(lineStart, lineEnd);
        boolean blankLine = line.isBlank();
        String indent = leadingWhitespace(line);
        String indented = indentSnippet(template, indent);

        int insertAt;
        String block;
        int snippetStart;
        String after;
        if (blankLine) {
            insertAt = lineStart;
            block = indented;
            snippetStart = insertAt;
            after = source.substring(lineEnd);
        } else {
            insertAt = lineEnd;
            block = "\n" + indented;
            snippetStart = insertAt + 1;
            after = source.substring(lineEnd);
        }
        if (!block.endsWith("\n") && (after.isEmpty() || after.charAt(0) != '\n')) {
            block += "\n";
        }

        String updated = source.substring(0, insertAt) + block + after;
        int snippetEnd = snippetStart + indented.length();
        List<Span> placeholders = new ArrayList<>();
        for (Span span : placeholders(updated)) {
            if (span.start() >= snippetStart && span.end() <= snippetEnd) {
                placeholders.add(span);
            }
        }
        int newCaret = placeholders.isEmpty() ? snippetEnd : placeholders.get(0).start();
        return new Insertion(updated, newCaret, List.copyOf(placeholders));
    }

    /** All placeholder spans in {@code text}, in reading order. */
    public static List<Span> placeholders(String text) {
        List<Span> spans = new ArrayList<>();
        for (int[] span : Placeholders.find(text)) {
            spans.add(new Span(span[0], span[1]));
        }
        return spans;
    }

    /**
     * Next placeholder after {@code current}, wrapping around; the first one when current is null.
     */
    public static Span nextPlaceholder(List<Span> spans, Span current) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        if (current == null) {
            return spans.get(0);
        }
        int index = spans.indexOf(current);
        if (index < 0) {
            return spans.get(0);
        }
        return spans.get((index + 1) % spans.size());
    }

    /** Previous placeholder before {@code current}, wrapping around. */
    public static Span previousPlaceholder(List<Span> spans, Span current) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        if (current == null) {
            return spans.get(spans.size() - 1);
        }
        int index = spans.indexOf(current);
        if (index < 0) {
            return spans.get(spans.size() - 1);
        }
        return spans.get((index - 1 + spans.size()) % spans.size());
    }

    /** Replaces a span and returns the resulting text. */
    public static String replace(String text, Span span, String replacement) {
        String source = text == null ? "" : text;
        int start = Math.max(0, Math.min(span.start(), source.length()));
        int end = Math.max(start, Math.min(span.end(), source.length()));
        return source.substring(0, start) + (replacement == null ? "" : replacement)
                + source.substring(end);
    }

    /** Start offset of the line containing {@code offset}. */
    public static int lineStart(String text, int offset) {
        String source = text == null ? "" : text;
        int at = Math.max(0, Math.min(offset, source.length()));
        return source.lastIndexOf('\n', Math.max(0, at - 1)) + 1;
    }

    /** End offset (the newline or the end of the text) of the line containing {@code offset}. */
    public static int lineEnd(String text, int offset) {
        String source = text == null ? "" : text;
        int at = Math.max(0, Math.min(offset, source.length()));
        int newline = source.indexOf('\n', at);
        return newline < 0 ? source.length() : newline;
    }

    /** Leading whitespace (spaces/tabs) of the line containing {@code offset}. */
    public static String lineIndent(String text, int offset) {
        String source = text == null ? "" : text;
        int start = lineStart(source, offset);
        int end = lineEnd(source, offset);
        return leadingWhitespace(source.substring(start, end));
    }

    /**
     * Indentation to insert after pressing Enter at {@code caret}: the current line's indentation,
     * plus two spaces after an opening brace, or one level less when the caret sits just before a
     * closing brace.
     */
    public static String indentForNewLine(String text, int caret) {
        String source = text == null ? "" : text;
        int at = Math.max(0, Math.min(caret, source.length()));
        int start = lineStart(source, at);
        int end = lineEnd(source, at);
        String line = source.substring(start, end);
        String indent = leadingWhitespace(line);
        String beforeCaret = source.substring(start, Math.min(at, end));
        String trimmedBefore = stripTrailing(beforeCaret);
        String trimmedLine = stripLeading(line);
        if (trimmedLine.startsWith("}") && trimmedBefore.isEmpty()) {
            return dedent(indent);
        }
        if (trimmedBefore.endsWith("{")) {
            return indent + "  ";
        }
        return indent;
    }

    /** Removes one tab or up to two leading spaces from a line. */
    public static String dedent(String line) {
        String source = line == null ? "" : line;
        if (source.startsWith("\t")) {
            return source.substring(1);
        }
        int remove = 0;
        while (remove < source.length() && remove < 2 && source.charAt(remove) == ' ') {
            remove++;
        }
        return source.substring(remove);
    }

    /**
     * Dedents every line touched by the selection. An empty selection dedents the caret's line.
     * Returns the new text; callers recompute the selection offsets.
     */
    public static String dedentLines(String text, int selectionStart, int selectionEnd) {
        String source = text == null ? "" : text;
        int start = Math.max(0, Math.min(selectionStart, source.length()));
        int end = Math.max(start, Math.min(selectionEnd, source.length()));
        int firstLineStart = lineStart(source, start);
        int lastLineEnd = lineEnd(source, end);
        String block = source.substring(firstLineStart, lastLineEnd);
        StringBuilder out = new StringBuilder();
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(dedent(lines[i]));
        }
        return source.substring(0, firstLineStart) + out + source.substring(lastLineEnd);
    }

    /**
     * Number of open braces before {@code offset}, ignoring braces inside double-quoted strings and
     * {@code #} comments.
     */
    public static int braceDepth(String text, int offset) {
        String source = text == null ? "" : text;
        int limit = Math.max(0, Math.min(offset, source.length()));
        int depth = 0;
        boolean inString = false;
        boolean inComment = false;
        boolean escaped = false;
        for (int i = 0; i < limit; i++) {
            char c = source.charAt(i);
            if (inComment) {
                if (c == '\n') {
                    inComment = false;
                }
                continue;
            }
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '#') {
                inComment = true;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }

    /**
     * True when a command with {@code scope} can be inserted at {@code braceDepth}.
     *
     * <p>
     * A {@code null} scope means "no scope restriction": the caller is not in the PROGRAM body
     * (e.g. the Scenario tabs {@code on build}/{@code on start}, which have their own context and
     * pass {@code null}), so insertion is always allowed. Scope only describes PROGRAM commands.
     */
    public static boolean isInsertionAllowed(GrammarReference.Scope scope, int braceDepth) {
        if (scope == null) {
            return true;
        }
        return switch (scope) {
            case BOTH -> true;
            case BODY_ONLY -> braceDepth == 0;
            case BLOCK_ONLY -> braceDepth > 0;
        };
    }

    /** Compact tag shown on a quick-reference command row. */
    public static String scopeTag(GrammarReference.Scope scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case BODY_ONLY -> "[top]";
            case BLOCK_ONLY -> "[in {}]";
            case BOTH -> "";
        };
    }

    /** Short hint shown next to a command in the quick reference and its preview. */
    public static String scopeHint(GrammarReference.Scope scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case BODY_ONLY -> "top-level only";
            case BLOCK_ONLY -> "inside { }";
            case BOTH -> "";
        };
    }

    /** Message shown when the caret is in the wrong scope for a command. */
    public static String mismatchMessage(GrammarReference.Scope scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case BODY_ONLY -> "This command only works at top level (outside { }).";
            case BLOCK_ONLY -> "This command only works inside a trigger or time block ({ }).";
            case BOTH -> "";
        };
    }

    private static String normalise(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String indentSnippet(String snippet, String indent) {
        StringBuilder out = new StringBuilder();
        String[] lines = snippet.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            if (!lines[i].isEmpty()) {
                out.append(indent);
            }
            out.append(lines[i]);
        }
        return out.toString();
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    private static String stripLeading(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(i);
    }

    private static String stripTrailing(String text) {
        int i = text.length();
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(0, i);
    }
}
