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
 * Pinta un mapa de muestra con la familia clara u oscura en día/crepúsculo/noche para afinar los
 * valores a ojo. Teclas: {@code 1/2/3} franja, {@code f} familia, {@code c} color (RGB 24-bit vs
 * slots ANSI del tema), {@code a} auto, {@code q}/Esc salir.
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
    private static final String[] PHASE_NAMES = {"DÍA", "CREPÚSCULO", "NOCHE"};
    private static final TextColor.ANSI[] ANSI_SLOTS = {TextColor.ANSI.BLACK, TextColor.ANSI.RED,
            TextColor.ANSI.GREEN, TextColor.ANSI.YELLOW, TextColor.ANSI.BLUE,
            TextColor.ANSI.MAGENTA, TextColor.ANSI.CYAN, TextColor.ANSI.WHITE,
            TextColor.ANSI.BLACK_BRIGHT, TextColor.ANSI.RED_BRIGHT, TextColor.ANSI.GREEN_BRIGHT,
            TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.MAGENTA_BRIGHT,
            TextColor.ANSI.CYAN_BRIGHT, TextColor.ANSI.WHITE_BRIGHT};

    private int family;
    private int phase;
    private boolean rgbMode = true;
    private boolean auto;
    private long lastAuto;

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
            if (auto && System.currentTimeMillis() - lastAuto > 1500) {
                phase = (phase + 1) % PHASE_NAMES.length;
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
        if (key.getKeyType() != KeyType.Character) {
            return true;
        }
        switch (Character.toLowerCase(key.getCharacter())) {
            case '1' -> phase = 0;
            case '2' -> phase = 1;
            case '3' -> phase = 2;
            case 'f' -> family = (family + 1) % FAMILY_NAMES.length;
            case 'c' -> rgbMode = !rgbMode;
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

    private void draw(TextGraphics tg, Screen screen) {
        int cols = screen.getTerminalSize().getColumns();
        int rows = screen.getTerminalSize().getRows();
        TextColor chromeBg = ansi(0x000000);
        TextColor chromeFg = ansi(0xCCCCCC);

        tg.setForegroundColor(chromeFg);
        tg.setBackgroundColor(chromeBg);
        tg.fill(' ');

        put(tg, 1, 0, "PALETTE LAB — día/noche 2D (ADR-022 fase 1)", ansi(0xFFFFFF), chromeBg);
        put(tg, 1, 1,
                "familia=" + FAMILY_NAMES[family] + "  franja=" + PHASE_NAMES[phase] + "  color="
                        + (rgbMode ? "RGB 24-bit" : "ANSI (tema del terminal)") + "  "
                        + (auto ? "auto ON" : "auto off"),
                ansi(0xFFC850), chromeBg);
        put(tg, 1, 2, "[1] día  [2] crepúsculo  [3] noche  [f] familia  [c] color  [a] auto  "
                + "[q] salir", chromeFg, chromeBg);

        Pal pal = palettes()[family][phase];
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

        if (!rgbMode && y + 2 < rows) {
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
        return rgbMode ? new TextColor.RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)
                : ansi(rgb);
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
