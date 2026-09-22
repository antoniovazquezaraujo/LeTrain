package letrain.visitor.terminal;

import com.googlecode.lanterna.TextColor;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Paleta día/noche del cliente 2D (ADR-022 fase 1d, ver
 * {@code docs/developer/systems/DayNight_Colors.md}). Familia clara: de día el mapa es papel con
 * glifos oscuros y de noche se funde a oscuro con glifos claros; en la transición se invierte la
 * polaridad y un suelo de contraste evita cualquier banda ilegible.
 *
 * <p>
 * Es data pura y matemáticas: no conoce Lanterna salvo al traducir el RGB al modo que soporte el
 * terminal (24-bit, 256 colores o los 16 ANSI del tema). Los valores salen del laboratorio de
 * paleta ({@code letrain.lab.terminal.PaletteLab}).
 */
public final class TerminalPalette {

    private static final Logger log = LoggerFactory.getLogger(TerminalPalette.class);

    public enum Token {
        GROUND, WATER, ROCK, RAIL, RAIL_INACTIVE, RAIL_INVALID, STATION, STATION_SELECTED, PRODUCER, CONSUMER, SENSOR, SEMAPHORE_OPEN, SEMAPHORE_CLOSED, SIGNAL_MAX, SIGNAL_MIN, DEAD_END, TUNNEL, BRIDGE, LOCO, WAGON, CARGO_COAL, CARGO_GOLD, CARGO_RUBY, CURSOR_DRAWING, CURSOR_MOVING, CURSOR_ERASING, HIGHLIGHT, LABEL, BOARD, FORK, FORK_SELECTED, SELECTION_LINK, CRASH
    }

    /** Modo de color del terminal, de mejor a peor fidelidad. */
    public enum Depth {
        TRUECOLOR, INDEXED_256, ANSI_16
    }

    /** Ratio del reloj en el que el papel claro alcanza su crepúsculo (0.5 = 20:00). */
    public static final float LIGHT_NIGHTFALL = 0.5f;

    /** Diferencia mínima de luminosidad entre un token y el fondo. */
    public static final double MIN_CONTRAST = 55;

    /**
     * Escalones de ratio de la paleta 2D. A diferencia del laboratorio (que interpola para afinar),
     * el cliente cuantiza: cada cambio de paleta obliga al terminal a repintar el mapa entero, y
     * interpolar cada minuto de juego producía un parpadeo por segundo en pantalla clara. Con 19
     * escalones (20 niveles) la transición es casi continua; el repintado de cada cambio es el
     * precio asumido.
     */
    public static final int BANDS = 19;

    /** Margen de histéresis alrededor del escalón actual, en unidades de ratio. */
    public static final float BAND_MARGIN = 0.03f;

    // Claves día / crepúsculo / noche, en el orden de Token.
    private static final int[] DAY = {rgb(242, 240, 232), rgb(40, 90, 190), rgb(170, 60, 60),
            rgb(50, 50, 55), rgb(150, 150, 150), rgb(200, 160, 0), rgb(25, 25, 30),
            rgb(200, 30, 30), rgb(170, 130, 0), rgb(90, 85, 70), rgb(0, 130, 130), rgb(0, 130, 60),
            rgb(200, 30, 30), rgb(200, 30, 30), rgb(40, 70, 170), rgb(200, 160, 0), rgb(90, 90, 95),
            rgb(70, 70, 80), rgb(40, 40, 45), rgb(90, 90, 95), rgb(20, 20, 20), rgb(150, 120, 0),
            rgb(180, 0, 50), rgb(0, 110, 40), rgb(170, 140, 0), rgb(190, 40, 40), rgb(25, 25, 30),
            rgb(60, 60, 65), rgb(242, 240, 232), rgb(25, 25, 30), rgb(200, 30, 30),
            rgb(150, 20, 120), rgb(200, 60, 30)};
    private static final int[] DUSK = {rgb(186, 183, 178), rgb(45, 80, 165), rgb(160, 70, 55),
            rgb(60, 52, 48), rgb(140, 130, 115), rgb(180, 140, 20), rgb(60, 40, 25),
            rgb(190, 50, 40), rgb(160, 115, 10), rgb(95, 80, 60), rgb(20, 120, 120),
            rgb(20, 115, 55), rgb(190, 50, 40), rgb(190, 50, 40), rgb(50, 75, 160),
            rgb(180, 140, 20), rgb(95, 85, 75), rgb(85, 75, 65), rgb(55, 45, 40), rgb(95, 85, 75),
            rgb(35, 30, 28), rgb(160, 115, 10), rgb(175, 30, 55), rgb(20, 110, 45),
            rgb(160, 120, 10), rgb(180, 55, 45), rgb(70, 55, 40), rgb(80, 65, 55),
            rgb(186, 183, 178), rgb(60, 40, 25), rgb(190, 50, 40), rgb(150, 40, 120),
            rgb(190, 70, 40)};
    private static final int[] NIGHT = {rgb(22, 25, 35), rgb(60, 95, 180), rgb(150, 100, 95),
            rgb(150, 150, 160), rgb(70, 72, 80), rgb(200, 190, 70), rgb(225, 228, 240),
            rgb(235, 90, 80), rgb(230, 200, 40), rgb(215, 210, 190), rgb(90, 200, 205),
            rgb(80, 200, 120), rgb(235, 90, 80), rgb(235, 90, 80), rgb(95, 125, 235),
            rgb(200, 190, 70), rgb(130, 135, 160), rgb(160, 165, 185), rgb(210, 212, 225),
            rgb(190, 192, 205), rgb(45, 45, 55), rgb(230, 200, 40), rgb(235, 40, 90),
            rgb(70, 190, 90), rgb(230, 200, 40), rgb(235, 90, 80), rgb(225, 228, 240),
            rgb(160, 165, 180), rgb(22, 25, 35), rgb(225, 228, 240), rgb(235, 90, 80),
            rgb(230, 120, 210), rgb(255, 120, 60)};

    private final Depth depth;

    public TerminalPalette(Depth depth) {
        this.depth = depth;
    }

    /** Detecta el modo de color del terminal con las convenciones habituales. */
    public static TerminalPalette detect() {
        String colorterm = System.getenv("COLORTERM");
        String term = System.getenv("TERM");
        if (colorterm != null && (colorterm.toLowerCase().contains("truecolor")
                || colorterm.toLowerCase().contains("24bit"))) {
            return new TerminalPalette(Depth.TRUECOLOR);
        }
        if (term != null && term.contains("direct")) {
            log.info("terminal palette: TRUECOLOR (COLORTERM={}, TERM={})", colorterm, term);
            return new TerminalPalette(Depth.TRUECOLOR);
        }
        Depth depth = term == null || term.isBlank() || term.contains("dumb") ? Depth.ANSI_16
                : Depth.INDEXED_256;
        log.info("terminal palette: {} (COLORTERM={}, TERM={})", depth, colorterm, term);
        return new TerminalPalette(depth);
    }

    /**
     * Escalón de ratio para el actual, con histéresis: se queda en el escalón vigente hasta que el
     * ratio se aleja más de medio escalón (más margen), así no baila en las fronteras.
     */
    public static float band(float ratio, float currentBand) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        if (currentBand < 0f) {
            return Math.round(clamped * BANDS) / (float) BANDS;
        }
        float half = 0.5f / BANDS;
        if (clamped > currentBand + half + BAND_MARGIN
                || clamped < currentBand - half - BAND_MARGIN) {
            return Math.round(clamped * BANDS) / (float) BANDS;
        }
        return currentBand;
    }

    /** Tokens resueltos para el ratio día/noche del reloj (0 = día, 1 = noche). */
    public record Resolved(Map<Token, Integer> rgb, Map<Token, TextColor> colors) {

        public int rgb(Token token) {
            return rgb.get(token);
        }

        public TextColor color(Token token) {
            return colors.get(token);
        }
    }

    /** Resuelve RGB y colores de terminal para un ratio. */
    public Resolved resolve(float ratio) {
        Map<Token, Integer> rgb = rgbFor(ratio);
        Map<Token, TextColor> colors = new EnumMap<>(Token.class);
        rgb.forEach((token, value) -> colors.put(token, colorOf(value)));
        return new Resolved(rgb, colors);
    }

    /** RGB por token para un ratio; puro y determinista. */
    static Map<Token, Integer> rgbFor(float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        Map<Token, Integer> resolved = new EnumMap<>(Token.class);
        int board = blendKey(Token.BOARD, clamped);
        resolved.put(Token.BOARD, board);
        for (Token token : Token.values()) {
            if (token == Token.BOARD || token == Token.GROUND) {
                // El campo es el mismo papel que el fondo: no se fuerza contraste.
                resolved.put(token, blendKey(token, clamped));
                continue;
            }
            resolved.put(token, contrast(blendKey(token, clamped), luminance(board)));
        }
        return resolved;
    }

    private static int blendKey(Token token, float ratio) {
        int day = DAY[token.ordinal()];
        int dusk = DUSK[token.ordinal()];
        int night = NIGHT[token.ordinal()];
        if (ratio <= 0f) {
            return day;
        }
        if (ratio >= 1f) {
            return night;
        }
        float paper = Math.min(1f, ratio / LIGHT_NIGHTFALL);
        int duskPaper = mix(day, dusk, paper);
        if (ratio <= LIGHT_NIGHTFALL) {
            return duskPaper;
        }
        float nightfall = (ratio - LIGHT_NIGHTFALL) / (1f - LIGHT_NIGHTFALL);
        return mix(duskPaper, night, nightfall);
    }

    /** Empuja un color hacia blanco o negro lo justo para separarse del fondo. */
    static int contrast(int rgb, double backgroundLuminance) {
        double value = luminance(rgb);
        if (value >= backgroundLuminance) {
            return pushTo(rgb, backgroundLuminance + MIN_CONTRAST, true);
        }
        return pushTo(rgb, backgroundLuminance - MIN_CONTRAST, false);
    }

    private static int pushTo(int rgb, double target, boolean towardWhite) {
        double clamped = Math.max(0, Math.min(255, target));
        boolean reached = towardWhite ? luminance(rgb) >= clamped : luminance(rgb) <= clamped;
        if (reached) {
            return rgb;
        }
        int other = towardWhite ? 0xFFFFFF : 0x000000;
        int best = other;
        for (int i = 1; i <= 100; i++) {
            best = mix(rgb, other, i / 100f);
            double value = luminance(best);
            if (towardWhite ? value >= clamped : value <= clamped) {
                return best;
            }
        }
        return best;
    }

    static double luminance(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }

    /** Mezcla en luz lineal (perceptualmente uniforme). */
    static int mix(int a, int b, float t) {
        int r = toSrgb(lerp(toLinear((a >> 16) & 0xFF), toLinear((b >> 16) & 0xFF), t));
        int g = toSrgb(lerp(toLinear((a >> 8) & 0xFF), toLinear((b >> 8) & 0xFF), t));
        int bl = toSrgb(lerp(toLinear(a & 0xFF), toLinear(b & 0xFF), t));
        return (r << 16) | (g << 8) | bl;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static double toLinear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int toSrgb(double linear) {
        double value =
                linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255);
    }

    /** Traduce un RGB al modo de color del terminal. */
    public TextColor colorOf(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return switch (depth) {
            case TRUECOLOR -> new TextColor.RGB(r, g, b);
            case INDEXED_256 -> nearestIndexed256(rgb);
            case ANSI_16 -> nearestAnsi(rgb);
        };
    }

    /** Niveles por canal del cubo 6x6x6 de la paleta xterm de 256 colores. */
    private static final int[] CUBE = {0, 95, 135, 175, 215, 255};

    /**
     * El color de la paleta de 256 más cercano al RGB pedido, buscando también en la rampa de
     * grises (índices 232-255). Lanterna solo mira el cubo 6x6x6, que se come esa rampa y colapsa
     * los tonos neutros (el papel día/noche pasaba de 20 niveles a 13, con saltos raros).
     */
    static TextColor nearestIndexed256(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int bestIndex = 16;
        long bestDistance = Long.MAX_VALUE;
        for (int cr = 0; cr < CUBE.length; cr++) {
            for (int cg = 0; cg < CUBE.length; cg++) {
                for (int cb = 0; cb < CUBE.length; cb++) {
                    long distance = distance(r, g, b, CUBE[cr], CUBE[cg], CUBE[cb]);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = 16 + 36 * cr + 6 * cg + cb;
                    }
                }
            }
        }
        for (int index = 232; index <= 255; index++) {
            int value = 8 + (index - 232) * 10;
            long distance = distance(r, g, b, value, value, value);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return new TextColor.Indexed(bestIndex);
    }

    private static long distance(int r, int g, int b, int cr, int cg, int cb) {
        long dr = r - cr;
        long dg = g - cg;
        long db = b - cb;
        return dr * dr + dg * dg + db * db;
    }

    /** El slot ANSI de 16 colores más cercano al RGB pedido. */
    static TextColor nearestAnsi(int rgb) {
        int[][] table = {{0x000000}, {0xCD0000}, {0x00CD00}, {0xCDCD00}, {0x0000EE}, {0xCD00CD},
                {0x00CDCD}, {0xE5E5E5}, {0x7F7F7F}, {0xFF0000}, {0x00FF00}, {0xFFFF00}, {0x5C5CFF},
                {0xFF00FF}, {0x00FFFF}, {0xFFFFFF},};
        TextColor[] slots = {TextColor.ANSI.BLACK, TextColor.ANSI.RED, TextColor.ANSI.GREEN,
                TextColor.ANSI.YELLOW, TextColor.ANSI.BLUE, TextColor.ANSI.MAGENTA,
                TextColor.ANSI.CYAN, TextColor.ANSI.WHITE, TextColor.ANSI.BLACK_BRIGHT,
                TextColor.ANSI.RED_BRIGHT, TextColor.ANSI.GREEN_BRIGHT,
                TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLUE_BRIGHT,
                TextColor.ANSI.MAGENTA_BRIGHT, TextColor.ANSI.CYAN_BRIGHT,
                TextColor.ANSI.WHITE_BRIGHT};
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < table.length; i++) {
            int cr = (table[i][0] >> 16) & 0xFF;
            int cg = (table[i][0] >> 8) & 0xFF;
            int cb = table[i][0] & 0xFF;
            long distance = (long) (r - cr) * (r - cr) + (long) (g - cg) * (g - cg)
                    + (long) (b - cb) * (b - cb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return slots[best];
    }

    static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
