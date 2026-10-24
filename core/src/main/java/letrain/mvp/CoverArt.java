package letrain.mvp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import letrain.utils.ValidationUtils;

/**
 * Coloured braille cover art for the 2D terminal (issue #771, part of arc #742): a 44-row by
 * 160-column picture of the RENFE 318 "Alco" with the {@code LeTrain} slant logo and its subtitle.
 *
 * <p>
 * This class is presentation-neutral: it only holds the <b>data</b> (a Unicode codepoint plus the
 * RGB foreground/background of every cell, either of which may be absent). The Lanterna paint and
 * the depth translation of the colours live in the terminal module
 * ({@code letrain.mvp.impl.terminal.CoverArtPainter}); {@code core} stays free of Lanterna.
 *
 * <p>
 * The data ships as a resource generated in the colour lab (see
 * {@code docs/developer/ui/RenderingDeepDive.md}): 44 lines, each one a run of fixed 16-character
 * cells, {@code cp4hex + fg6hex|------ + bg6hex|------} (a cell with both colours absent would be
 * an invisible glyph, so the generator never emits one). {@link #load()} reads the bundled file
 * once and caches the result.
 */
public final class CoverArt {

    /** Classpath resource bundled by {@code core} with the owner-approved braille cover. */
    public static final String RESOURCE = "/letrain/cover/cover_u44.data";

    /**
     * Subtitle under the logo ("The Letter Train Simulator"), baked into the 2D art and drawn as a
     * monospace overlay by the 3D cover (#801), so the two covers cannot drift apart.
     */
    public static final String TAGLINE = "The Letter Train Simulator";

    /** Natural width of the art in terminal columns; a narrower terminal crops it symmetrically. */
    public static final int COLUMNS = 160;

    /** Natural height of the art in terminal rows; a shorter terminal crops it symmetrically. */
    public static final int ROWS = 44;

    /** Characters per cell record: {@code cp4hex} + {@code fg6hex} + {@code bg6hex}. */
    static final int CELL_CHARS = 16;

    /** Characters of a colour field: 6 hex digits, or {@link #NO_COLOR}. */
    static final int COLOR_CHARS = 6;

    /** Characters of the codepoint field: 4 hex digits. */
    static final int CODE_POINT_CHARS = CELL_CHARS - 2 * COLOR_CHARS;

    /** Colour field meaning "this cell has no colour here" (the renderer then falls back). */
    static final String NO_COLOR = "------";

    /**
     * One cell of the cover: a Unicode codepoint and optional RGB fg/bg ({@code null} = absent).
     */
    public record Cell(int codePoint, Integer fgRgb, Integer bgRgb) {
        public Cell {
            ValidationUtils.require(codePoint >= 0, "codePoint must not be negative");
            ValidationUtils.require(fgRgb == null || withinRgb(fgRgb), "fgRgb out of RGB range");
            ValidationUtils.require(bgRgb == null || withinRgb(bgRgb), "bgRgb out of RGB range");
        }

        private static boolean withinRgb(int rgb) {
            return rgb >= 0 && rgb <= 0xFFFFFF;
        }
    }

    private final List<List<Cell>> rows;

    private CoverArt(List<List<Cell>> rows) {
        this.rows = List.copyOf(rows);
    }

    private static final Object LOCK = new Object();
    private static volatile CoverArt cached;

    /** The bundled cover, parsed once and cached; never {@code null}. */
    public static CoverArt load() {
        CoverArt local = cached;
        if (local == null) {
            synchronized (LOCK) {
                local = cached;
                if (local == null) {
                    local = readResource();
                    cached = local;
                }
            }
        }
        return local;
    }

    private static CoverArt readResource() {
        try (InputStream in = CoverArt.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "cover art resource not found on the classpath: " + RESOURCE);
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read cover art resource " + RESOURCE, e);
        }
    }

    /** Parses cover art from the fixed-cell text format; exposed for tests and tooling. */
    public static CoverArt parse(Reader reader) {
        ValidationUtils.requireNonNull(reader, "reader must not be null");
        BufferedReader buffered =
                reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        List<List<Cell>> parsed = new ArrayList<>();
        String line;
        int lineNumber = 0;
        try {
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                parsed.add(parseLine(line, lineNumber));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CoverArt(parsed);
    }

    /** Convenience for tests: parses the given lines as if read from the resource. */
    public static CoverArt parse(List<String> lines) {
        return parse(new StringReader(String.join("\n", lines)));
    }

    private static List<Cell> parseLine(String line, int lineNumber) {
        if (line.isEmpty() || line.length() % CELL_CHARS != 0) {
            throw new IllegalArgumentException("cover line " + lineNumber + " is not a run of "
                    + CELL_CHARS + "-char cells (length " + line.length() + ")");
        }
        List<Cell> cells = new ArrayList<>(line.length() / CELL_CHARS);
        for (int i = 0; i + CELL_CHARS <= line.length(); i += CELL_CHARS) {
            cells.add(parseCell(line.substring(i, i + CELL_CHARS), lineNumber, i / CELL_CHARS));
        }
        return List.copyOf(cells);
    }

    private static Cell parseCell(String raw, int lineNumber, int column) {
        int codePoint;
        try {
            codePoint = Integer.parseInt(raw.substring(0, CODE_POINT_CHARS), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "bad codepoint in cover cell " + lineNumber + ":" + column + " ('" + raw + "')",
                    e);
        }
        return new Cell(codePoint,
                parseColor(raw.substring(CODE_POINT_CHARS, CODE_POINT_CHARS + COLOR_CHARS),
                        lineNumber, column),
                parseColor(raw.substring(CODE_POINT_CHARS + COLOR_CHARS, CELL_CHARS), lineNumber,
                        column));
    }

    private static Integer parseColor(String field, int lineNumber, int column) {
        if (NO_COLOR.equals(field)) {
            return null;
        }
        try {
            return Integer.parseInt(field, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "bad colour in cover cell " + lineNumber + ":" + column + " ('" + field + "')",
                    e);
        }
    }

    /** Number of rows of the art. */
    public int height() {
        return rows.size();
    }

    /** Number of columns of the art (uses the widest row, so an uneven file still renders). */
    public int width() {
        return rows.stream().mapToInt(List::size).max().orElse(0);
    }

    /** The cells of a row, left to right; never {@code null}. */
    public List<Cell> row(int index) {
        return rows.get(index);
    }

    /** All rows, top to bottom. */
    public List<List<Cell>> rows() {
        return rows;
    }
}
