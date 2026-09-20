package letrain.lab.terminal;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import java.io.IOException;

/**
 * Laboratorio de la paleta día/noche del cliente 2D (fase 1 de ADR-022, ver
 * {@code docs/developer/systems/DayNight_Colors.md}).
 *
 * <p>
 * Pinta un mapa de muestra con la familia clara u oscura y una hora simulada, interpolando
 * día→crepúsculo→noche con la misma curva que el reloj del juego. Teclas: {@code ←/→} mueve la
 * hora, {@code 1/2/3} salta a día/crepúsculo/noche, {@code f} familia, {@code c} cicla el color
 * (RGB 24-bit → 256 colores → ANSI 16 del tema), {@code a} auto, {@code q}/Esc salir.
 *
 * <p>
 * Para ajustar la paleta, edita los valores de {@link #palettes()} y vuelve a lanzar. Este
 * laboratorio usa los mismos tokens que el mapa de colores; cuando exista el {@code VisualPalette}
 * real, el lab pasará a consumirlo.
 */
public class PaletteLab {

    /** Tokens de una combinación familia+franja (RGB 0xRRGGBB). */
    record Pal(int ground, int water, int rock, int rail, int railInactive, int railInvalid,
            int station, int stationSelected, int producer, int consumer, int sensor, int semOpen,
            int semClosed, int signalMax, int signalMin, int deadEnd, int tunnel, int bridge,
            int loco, int wagon, int cargoCoal, int cargoGold, int cargoRuby, int cursorDrawing,
            int cursorMoving, int cursorErasing, int highlight, int label) {}

    /** Mapa de muestra: los colores de cada token salen de la {@link Pal} activa. */
    private static final String[] MAP = {"                                        ",
            "   ~~~~~~        *****                  ", "   ~~~┬~~~       *****     ◆1 ●         ",
            "   ~~~~~~        *****                  ", "     │                                  ",
            "─────┼───────────╺        ₪      !      ", "     │                                  ",
            "     │      ◇2◌         :               ", "     │                                  ",
            "     >        █▭▪▲      5               ", "              ─┼─·x     3               ",
            "               │                        ", "      ⋂....⋂           ╳                ",
            "                                        ",};

    static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    /** [familia][franja]: familia 0 = clara (por defecto), 1 = oscura. */
    static Pal[][] palettes() {
        Pal[] light = {
                // Día "mapa papel": fondo claro, glifos oscuros.
                new Pal(rgb(242, 240, 232), rgb(40, 90, 190), rgb(170, 60, 60), rgb(50, 50, 55),
                        rgb(150, 150, 150), rgb(200, 160, 0), rgb(25, 25, 30), rgb(200, 30, 30),
                        rgb(170, 130, 0), rgb(90, 85, 70), rgb(0, 130, 130), rgb(0, 130, 60),
                        rgb(200, 30, 30), rgb(200, 30, 30), rgb(40, 70, 170), rgb(200, 160, 0),
                        rgb(90, 90, 95), rgb(70, 70, 80), rgb(40, 40, 45), rgb(90, 90, 95),
                        rgb(20, 20, 20), rgb(150, 120, 0), rgb(180, 0, 50), rgb(0, 110, 40),
                        rgb(170, 140, 0), rgb(190, 40, 40), rgb(25, 25, 30), rgb(60, 60, 65)),
                // Crepúsculo claro: papel gris cálido.
                new Pal(rgb(186, 183, 178), rgb(45, 80, 165), rgb(160, 70, 55), rgb(60, 52, 48),
                        rgb(140, 130, 115), rgb(180, 140, 20), rgb(60, 40, 25), rgb(190, 50, 40),
                        rgb(160, 115, 10), rgb(95, 80, 60), rgb(20, 120, 120), rgb(20, 115, 55),
                        rgb(190, 50, 40), rgb(190, 50, 40), rgb(50, 75, 160), rgb(180, 140, 20),
                        rgb(95, 85, 75), rgb(85, 75, 65), rgb(55, 45, 40), rgb(95, 85, 75),
                        rgb(35, 30, 28), rgb(160, 115, 10), rgb(175, 30, 55), rgb(20, 110, 45),
                        rgb(160, 120, 10), rgb(180, 55, 45), rgb(70, 55, 40), rgb(80, 65, 55)),
                // Noche: vuelve al look oscuro.
                new Pal(rgb(22, 25, 35), rgb(60, 95, 180), rgb(150, 100, 95), rgb(150, 150, 160),
                        rgb(70, 72, 80), rgb(200, 190, 70), rgb(225, 228, 240), rgb(235, 90, 80),
                        rgb(230, 200, 40), rgb(215, 210, 190), rgb(90, 200, 205), rgb(80, 200, 120),
                        rgb(235, 90, 80), rgb(235, 90, 80), rgb(95, 125, 235), rgb(200, 190, 70),
                        rgb(130, 135, 160), rgb(160, 165, 185), rgb(210, 212, 225),
                        rgb(190, 192, 205), rgb(45, 45, 55), rgb(230, 200, 40), rgb(235, 40, 90),
                        rgb(70, 190, 90), rgb(230, 200, 40), rgb(235, 90, 80), rgb(225, 228, 240),
                        rgb(160, 165, 180)),};
        Pal[] dark = {
                // Día oscuro: la actual con el campo como fondo negro.
                new Pal(rgb(12, 12, 14), rgb(90, 150, 255), rgb(230, 120, 120), rgb(90, 90, 95),
                        rgb(30, 30, 33), rgb(255, 255, 0), rgb(255, 255, 255), rgb(255, 80, 80),
                        rgb(255, 216, 0), rgb(229, 229, 204), rgb(80, 220, 220), rgb(80, 220, 120),
                        rgb(255, 80, 80), rgb(255, 80, 80), rgb(90, 130, 255), rgb(255, 255, 0),
                        rgb(120, 120, 120), rgb(160, 160, 170), rgb(153, 153, 153),
                        rgb(128, 128, 128), rgb(25, 25, 25), rgb(255, 216, 0), rgb(255, 0, 76),
                        rgb(60, 220, 60), rgb(255, 220, 0), rgb(255, 70, 70), rgb(255, 255, 0),
                        rgb(150, 150, 150)),
                // Crepúsculo oscuro.
                new Pal(rgb(16, 12, 12), rgb(70, 110, 190), rgb(195, 110, 95), rgb(80, 72, 68),
                        rgb(28, 25, 24), rgb(220, 200, 60), rgb(235, 200, 160), rgb(230, 80, 70),
                        rgb(230, 190, 40), rgb(210, 200, 175), rgb(80, 190, 190), rgb(70, 190, 110),
                        rgb(230, 80, 70), rgb(230, 80, 70), rgb(80, 115, 220), rgb(220, 200, 60),
                        rgb(110, 100, 90), rgb(145, 130, 120), rgb(140, 120, 110),
                        rgb(120, 110, 100), rgb(24, 22, 21), rgb(230, 190, 40), rgb(230, 60, 90),
                        rgb(60, 180, 60), rgb(230, 190, 40), rgb(230, 80, 70), rgb(235, 200, 160),
                        rgb(200, 160, 120)),
                // Noche oscura.
                new Pal(rgb(4, 6, 12), rgb(35, 55, 110), rgb(110, 80, 80), rgb(45, 45, 55),
                        rgb(16, 16, 20), rgb(180, 170, 60), rgb(140, 145, 165), rgb(200, 70, 70),
                        rgb(190, 160, 20), rgb(150, 148, 135), rgb(50, 140, 150), rgb(45, 140, 80),
                        rgb(190, 70, 70), rgb(200, 70, 70), rgb(55, 80, 170), rgb(180, 170, 60),
                        rgb(60, 65, 85), rgb(80, 85, 105), rgb(85, 88, 100), rgb(70, 72, 80),
                        rgb(22, 23, 28), rgb(190, 160, 20), rgb(190, 20, 60), rgb(35, 130, 45),
                        rgb(190, 170, 20), rgb(190, 70, 70), rgb(140, 145, 165),
                        rgb(95, 100, 115)),};
        return new Pal[][] {light, dark};
    }

    private static final String[] FAMILY_NAMES = {"CLARA (papel)", "OSCURA"};
    private static final TextColor.ANSI[] ANSI_SLOTS = {TextColor.ANSI.BLACK, TextColor.ANSI.RED,
            TextColor.ANSI.GREEN, TextColor.ANSI.YELLOW, TextColor.ANSI.BLUE,
            TextColor.ANSI.MAGENTA, TextColor.ANSI.CYAN, TextColor.ANSI.WHITE,
            TextColor.ANSI.BLACK_BRIGHT, TextColor.ANSI.RED_BRIGHT, TextColor.ANSI.GREEN_BRIGHT,
            TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.MAGENTA_BRIGHT,
            TextColor.ANSI.CYAN_BRIGHT, TextColor.ANSI.WHITE_BRIGHT};

    private int family;
    private double hour = 12.0;
    private int colorMode;
    private boolean cutMode;
    private boolean auto;
    private long lastAuto;

    private static final String[] COLOR_MODE_NAMES =
            {"RGB 24-bit", "256 colores", "ANSI 16 (tema)"};

    public static void main(String[] args) throws IOException {
        new PaletteLab().run();
    }

    private void run() throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setUnixTerminalCtrlCBehaviour(UnixLikeTerminal.CtrlCBehaviour.TRAP);
        Terminal terminal = factory.createTerminal();
        terminal.setCursorVisible(false);
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);
        try {
            loop(screen);
        } finally {
            try {
                screen.stopScreen();
            } catch (Exception ignored) {
                // already stopped
            }
            screen.close();
        }
    }

    private void loop(Screen screen) throws IOException {
        TextGraphics tg = screen.newTextGraphics();
        while (true) {
            if (screen.doResizeIfNecessary() != null) {
                tg = screen.newTextGraphics();
            }
            if (auto && System.currentTimeMillis() - lastAuto > 120) {
                hour = (hour + 0.1) % 24.0;
                lastAuto = System.currentTimeMillis();
            }
            draw(tg, screen);
            screen.refresh();

            KeyStroke key = screen.pollInput();
            if (key != null && !handle(key)) {
                return;
            }
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean handle(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            return false;
        }
        if (key.getKeyType() == KeyType.ArrowLeft) {
            hour = (hour + 24.0 - 5.0 / 60.0) % 24.0;
            return true;
        }
        if (key.getKeyType() == KeyType.ArrowRight) {
            hour = (hour + 5.0 / 60.0) % 24.0;
            return true;
        }
        if (key.getKeyType() != KeyType.Character) {
            return true;
        }
        switch (Character.toLowerCase(key.getCharacter())) {
            case '1' -> hour = 12.0;
            case '2' -> hour = 20.0;
            case '3' -> hour = 23.0;
            case 'f' -> family = (family + 1) % FAMILY_NAMES.length;
            case 'c' -> colorMode = (colorMode + 1) % COLOR_MODE_NAMES.length;
            case 't' -> cutMode = !cutMode;
            case 'a' -> {
                auto = !auto;
                lastAuto = System.currentTimeMillis();
            }
            case 'q' -> {
                return false;
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    /** Misma curva que {@code SimpleGameClock.getDayNightRatio()}: 0 de día, 1 de noche. */
    static double ratioOf(double hour) {
        double minute = hour * 60.0;
        if (minute >= 7 * 60 && minute <= 19 * 60) {
            return 0.0;
        }
        if (minute >= 21 * 60 || minute <= 5 * 60) {
            return 1.0;
        }
        if (minute > 19 * 60) {
            return (minute - 19 * 60) / (21 * 60 - 19 * 60);
        }
        return 1.0 - (minute - 5 * 60) / (7 * 60 - 5 * 60);
    }

    private static String phaseName(double ratio) {
        if (ratio <= 0.0) {
            return "día";
        }
        if (ratio >= 1.0) {
            return "noche";
        }
        return ratio < 0.5 ? "atardecer" : "amanecer";
    }

    /**
     * Transición gradual con el ratio del reloj. En la familia clara el día y la noche tienen
     * polaridad inversa (glifos oscuros sobre papel / glifos claros sobre negro), así que la
     * inversión se funde directamente de un régimen al otro a ritmo constante durante la hora
     * restante, con un suelo de contraste que evita la banda confusa; la familia oscura interpola
     * lineal.
     */
    static Pal blend(int family, double ratio) {
        return blend(family, ratio, false);
    }

    static Pal blend(int family, double ratio, boolean cut) {
        Pal[] keys = palettes()[family];
        if (ratio <= 0.0) {
            return keys[0];
        }
        if (ratio >= 1.0) {
            return keys[2];
        }
        if (family == 0 && cut) {
            if (ratio < CUT_RATIO) {
                return mix(keys[0], keys[1], (float) (ratio / CUT_RATIO));
            }
            return keys[2];
        }
        if (family == 0) {
            Pal paper = mix(keys[0], keys[1], (float) Math.min(1.0, ratio / LIGHT_NIGHTFALL));
            double nightfall = (ratio - LIGHT_NIGHTFALL) / (1.0 - LIGHT_NIGHTFALL);
            if (nightfall <= 0.0) {
                return ensureContrast(paper);
            }
            return ensureContrast(mix(paper, keys[2], (float) nightfall));
        }
        if (ratio <= 0.5) {
            return mix(keys[0], keys[1], (float) (ratio * 2.0));
        }
        return mix(keys[1], keys[2], (float) ((ratio - 0.5) * 2.0));
    }

    /**
     * El papel claro alcanza su tono de crepúsculo en este ratio (0.5 = 20:00) y a partir de ahí
     * empieza el fundido a oscuro, repartido por la hora restante para que no haya un salto.
     */
    static final double LIGHT_NIGHTFALL = 0.5;

    /** Ratio del corte seco de régimen en la familia clara (0.75 = 20:30). */
    static final double CUT_RATIO = 0.75;

    /**
     * Diferencia mínima de luminosidad entre cada token y el fondo. Si al fundir se acercan, el
     * token se aclara u oscurece lo justo para seguir distinguiéndose (nunca hay banda confusa).
     */
    static final double MIN_CONTRAST = 55;

    static Pal mix(Pal a, Pal b, float t) {
        return new Pal(mixColor(a.ground(), b.ground(), t), mixColor(a.water(), b.water(), t),
                mixColor(a.rock(), b.rock(), t), mixColor(a.rail(), b.rail(), t),
                mixColor(a.railInactive(), b.railInactive(), t),
                mixColor(a.railInvalid(), b.railInvalid(), t),
                mixColor(a.station(), b.station(), t),
                mixColor(a.stationSelected(), b.stationSelected(), t),
                mixColor(a.producer(), b.producer(), t), mixColor(a.consumer(), b.consumer(), t),
                mixColor(a.sensor(), b.sensor(), t), mixColor(a.semOpen(), b.semOpen(), t),
                mixColor(a.semClosed(), b.semClosed(), t),
                mixColor(a.signalMax(), b.signalMax(), t),
                mixColor(a.signalMin(), b.signalMin(), t), mixColor(a.deadEnd(), b.deadEnd(), t),
                mixColor(a.tunnel(), b.tunnel(), t), mixColor(a.bridge(), b.bridge(), t),
                mixColor(a.loco(), b.loco(), t), mixColor(a.wagon(), b.wagon(), t),
                mixColor(a.cargoCoal(), b.cargoCoal(), t),
                mixColor(a.cargoGold(), b.cargoGold(), t),
                mixColor(a.cargoRuby(), b.cargoRuby(), t),
                mixColor(a.cursorDrawing(), b.cursorDrawing(), t),
                mixColor(a.cursorMoving(), b.cursorMoving(), t),
                mixColor(a.cursorErasing(), b.cursorErasing(), t),
                mixColor(a.highlight(), b.highlight(), t), mixColor(a.label(), b.label(), t));
    }

    static int mixColor(int a, int b, float t) {
        int r = toSrgb(lerp(toLinear((a >> 16) & 0xFF), toLinear((b >> 16) & 0xFF), t));
        int g = toSrgb(lerp(toLinear((a >> 8) & 0xFF), toLinear((b >> 8) & 0xFF), t));
        int bl = toSrgb(lerp(toLinear(a & 0xFF), toLinear(b & 0xFF), t));
        return (r << 16) | (g << 8) | bl;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static double toLinear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int toSrgb(double linear) {
        double v = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, v)) * 255);
    }

    /** Fuerza que cada token mantenga {@link #MIN_CONTRAST} de luminosidad frente al fondo. */
    static Pal ensureContrast(Pal p) {
        double bg = luminance(p.ground());
        return new Pal(p.ground(), contrast(p.water(), bg), contrast(p.rock(), bg),
                contrast(p.rail(), bg), contrast(p.railInactive(), bg),
                contrast(p.railInvalid(), bg), contrast(p.station(), bg),
                contrast(p.stationSelected(), bg), contrast(p.producer(), bg),
                contrast(p.consumer(), bg), contrast(p.sensor(), bg), contrast(p.semOpen(), bg),
                contrast(p.semClosed(), bg), contrast(p.signalMax(), bg),
                contrast(p.signalMin(), bg), contrast(p.deadEnd(), bg), contrast(p.tunnel(), bg),
                contrast(p.bridge(), bg), contrast(p.loco(), bg), contrast(p.wagon(), bg),
                contrast(p.cargoCoal(), bg), contrast(p.cargoGold(), bg),
                contrast(p.cargoRuby(), bg), contrast(p.cursorDrawing(), bg),
                contrast(p.cursorMoving(), bg), contrast(p.cursorErasing(), bg),
                contrast(p.highlight(), bg), contrast(p.label(), bg));
    }

    private static int contrast(int rgb, double bgLuminance) {
        return luminance(rgb) >= bgLuminance ? pushTo(rgb, bgLuminance + MIN_CONTRAST, true)
                : pushTo(rgb, bgLuminance - MIN_CONTRAST, false);
    }

    /** Mezcla el color hacia blanco o negro lo justo para alcanzar la luminosidad objetivo. */
    private static int pushTo(int rgb, double target, boolean towardWhite) {
        double clamped = Math.max(0, Math.min(255, target));
        boolean reached = towardWhite ? luminance(rgb) >= clamped : luminance(rgb) <= clamped;
        if (reached) {
            return rgb;
        }
        int other = towardWhite ? 0xFFFFFF : 0x000000;
        int best = other;
        for (int i = 1; i <= 100; i++) {
            best = mixColor(rgb, other, i / 100f);
            double l = luminance(best);
            if (towardWhite ? l >= clamped : l <= clamped) {
                return best;
            }
        }
        return best;
    }

    static double luminance(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }

    private void draw(TextGraphics tg, Screen screen) {
        int cols = screen.getTerminalSize().getColumns();
        int rows = screen.getTerminalSize().getRows();
        TextColor chromeBg = ansi(0x000000);
        TextColor chromeFg = ansi(0xCCCCCC);

        tg.setForegroundColor(chromeFg);
        tg.setBackgroundColor(chromeBg);
        tg.fill(' ');

        double ratio = ratioOf(hour);
        put(tg, 1, 0, "PALETTE LAB — día/noche 2D (ADR-022 fase 1)", ansi(0xFFFFFF), chromeBg);
        put(tg, 1, 1,
                String.format(
                        "hora=%02d:%02d ratio=%.2f (%s)  familia=%s  color=%s  transición=%s  %s",
                        (int) hour, (int) (hour % 1 * 60), ratio, phaseName(ratio),
                        FAMILY_NAMES[family], COLOR_MODE_NAMES[colorMode],
                        cutMode ? "corte" : "fundido", auto ? "auto ON" : "auto off"),
                ansi(0xFFC850), chromeBg);
        put(tg, 1, 2, "[←/→] hora  [1] día  [2] crepúsculo  [3] noche  [f] familia  [c] color  "
                + "[t] transición  [a] auto  [q] salir", chromeFg, chromeBg);

        Pal pal = blend(family, ratio, cutMode);
        int mapY = 4;
        for (int y = 0; y < MAP.length && mapY + y < rows - 10; y++) {
            String line = MAP[y];
            for (int x = 0; x < line.length() && x + 2 < cols; x++) {
                char ch = line.charAt(x);
                put(tg, 2 + x, mapY + y, ch, color(tokenColor(pal, ch)), color(pal.ground()));
            }
        }

        int y = mapY + MAP.length + 1;
        put(tg, 1, y++, "tokens (cuadrado = color; nombre al lado):", chromeFg, chromeBg);
        drawSwatches(tg, cols, y, pal, chromeFg, chromeBg);

        y += 4;
        put(tg, 1, y++, "valores actuales (edítalos en palettes() para afinar):", chromeFg,
                chromeBg);
        y = drawHexList(tg, cols, y, pal, chromeFg, chromeBg);

        if (colorMode == 1 && y + 2 < rows) {
            put(tg, 1, y++, "rampa de grises 256 (slots 232-255):", chromeFg, chromeBg);
            for (int i = 232; i <= 255 && 2 + (i - 232) * 2 < cols; i++) {
                put(tg, 2 + (i - 232) * 2, y, "  ", chromeFg, new TextColor.Indexed(i));
            }
        } else if (colorMode == 2 && y + 2 < rows) {
            put(tg, 1, y++, "slots ANSI de tu tema:", chromeFg, chromeBg);
            for (int i = 0; i < ANSI_SLOTS.length && 2 + i * 3 < cols; i++) {
                put(tg, 2 + i * 3, y, "  ", chromeFg, ANSI_SLOTS[i]);
            }
        }
        put(tg, 1, rows - 1,
                "el campo no tiene glifo: es el fondo · familia clara = papel de día / oscuro de noche",
                ansi(0x777777), chromeBg);
    }

    private void drawSwatches(TextGraphics tg, int cols, int y, Pal pal, TextColor fg,
            TextColor bg) {
        int x = 2;
        for (Object[] entry : tokenEntries(pal)) {
            String name = (String) entry[0];
            int tokenRgb = (int) entry[1];
            int width = 4 + name.length();
            if (x + width > cols - 2) {
                x = 2;
                y++;
            }
            put(tg, x, y, "  ", fg, color(tokenRgb));
            put(tg, x + 3, y, name, fg, bg);
            x += width;
        }
    }

    private int drawHexList(TextGraphics tg, int cols, int y, Pal pal, TextColor fg, TextColor bg) {
        StringBuilder line = new StringBuilder();
        int indent = 2;
        for (Object[] entry : tokenEntries(pal)) {
            String piece = String.format("%s=#%06X", entry[0], (int) entry[1]);
            if (indent + line.length() + piece.length() + 2 > cols - 1) {
                put(tg, indent, y++, line.toString(), fg, bg);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append("  ");
            }
            line.append(piece);
        }
        if (line.length() > 0) {
            put(tg, indent, y++, line.toString(), fg, bg);
        }
        return y;
    }

    private static Object[][] tokenEntries(Pal p) {
        return new Object[][] {{"campo", p.ground()}, {"agua", p.water()}, {"roca", p.rock()},
                {"via", p.rail()}, {"via.inact", p.railInactive()},
                {"via.invalida", p.railInvalid()}, {"estacion", p.station()},
                {"estacion.sel", p.stationSelected()}, {"productor", p.producer()},
                {"consumidor", p.consumer()}, {"sensor", p.sensor()},
                {"semaforo.abierto", p.semOpen()}, {"semaforo.cerrado", p.semClosed()},
                {"senal.max", p.signalMax()}, {"senal.min", p.signalMin()},
                {"via.muerta", p.deadEnd()}, {"tunel", p.tunnel()}, {"puente", p.bridge()},
                {"loco", p.loco()}, {"vagon", p.wagon()}, {"carga.carbon", p.cargoCoal()},
                {"carga.oro", p.cargoGold()}, {"carga.rubi", p.cargoRuby()},
                {"cursor", p.cursorDrawing()}, {"resalte", p.highlight()},
                {"etiqueta", p.label()},};
    }

    private static int tokenColor(Pal p, char ch) {
        return switch (ch) {
            case '~' -> p.water();
            case '*' -> p.rock();
            case '─', '│', '┼', '╳' -> p.rail();
            case '·' -> p.railInactive();
            case 'x' -> p.railInvalid();
            case '╺' -> p.deadEnd();
            case '.' -> p.tunnel();
            case '⋂' -> p.tunnel();
            case '┬' -> p.bridge();
            case '◇' -> p.station();
            case '◆' -> p.stationSelected();
            case '●' -> p.producer();
            case '◌' -> p.consumer();
            case '₪' -> p.sensor();
            case '!' -> p.semOpen();
            case ':' -> p.semClosed();
            case '5' -> p.signalMax();
            case '3' -> p.signalMin();
            case '█' -> p.loco();
            case '▭' -> p.wagon();
            case '▪' -> p.cargoCoal();
            case '▲' -> p.cargoGold();
            case '>' -> p.cursorDrawing();
            default -> p.ground();
        };
    }

    private TextColor color(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return switch (colorMode) {
            case 1 -> TextColor.Indexed.fromRGB(r, g, b);
            case 2 -> ansi(rgb);
            default -> new TextColor.RGB(r, g, b);
        };
    }

    private void put(TextGraphics tg, int x, int y, String text, TextColor fg, TextColor bg) {
        for (int i = 0; i < text.length(); i++) {
            put(tg, x + i, y, text.charAt(i), fg, bg);
        }
    }

    private void put(TextGraphics tg, int x, int y, char ch, TextColor fg, TextColor bg) {
        tg.setCharacter(x, y, TextCharacter.fromCharacter(ch, fg, bg)[0]);
    }

    /** Fallback a 16 colores: el slot ANSI mas cercano al RGB pedido. */
    static TextColor ansi(int rgb) {
        int[][] table = {{0x000000}, {0xCD0000}, {0x00CD00}, {0xCDCD00}, {0x0000EE}, {0xCD00CD},
                {0x00CDCD}, {0xE5E5E5}, {0x7F7F7F}, {0xFF0000}, {0x00FF00}, {0xFFFF00}, {0x5C5CFF},
                {0xFF00FF}, {0x00FFFF}, {0xFFFFFF},};
        TextColor.ANSI[] slots = {TextColor.ANSI.BLACK, TextColor.ANSI.RED, TextColor.ANSI.GREEN,
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
}
