package letrain.ground.impl;

import java.io.IOException;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import letrain.ground.PerlinNoise;

public class TerrainGenerationLab {
    private DefaultTerminalFactory terminalFactory;
    private Terminal terminal;
    private TerminalSize terminalSize;
    private TextGraphics centralGraphics;
    private Screen screen;
    private TextColor fgColor;
    private TextColor bgColor;

    public TerrainGenerationLab() {
        this.fgColor = TextColor.ANSI.DEFAULT;
        this.bgColor = TextColor.ANSI.DEFAULT;
        terminalFactory = new DefaultTerminalFactory();
        try {
            terminal = terminalFactory.createTerminal();
            terminal.setCursorVisible(false);
            this.screen = createScreen(terminal);
        } catch (IOException e) {
            e.printStackTrace();
        }
        terminalSize = screen.getTerminalSize();

        centralGraphics = screen.newTextGraphics();

    }

    Screen createScreen(Terminal terminal) throws IOException {
        Screen screen;
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);
        return screen;
    }

    public static void main(String[] args) throws IOException {
        new TerrainGenerationLab().start();
    }

    public void start() throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        terminal.enterPrivateMode();
        terminal.clearScreen();
        TerminalSize size = terminal.getTerminalSize();

        PerlinNoise noise = new PerlinNoise(254);
        int octaves = 5;
        int col = -100;
        int row = -100;
        int water = 113;
        int ground = 158;
        int mountain = 200;
        while (true) {
            for (int y = 0; y < 30; y += 1) {
                for (int x = 0; x < 100; x += 1) {
                    float rand = (noise.smoothNoise(Math.abs((col + x) * 0.01F), Math.abs((row + y) * 0.02F), 0,
                            octaves));
                    rand = scaleAndShift(rand, -0.7F, 0.7F, 0F, 255F);
                    int intColor = (int) rand;
                    if (rand < water) {
                        centralGraphics.setForegroundColor(new TextColor.RGB(0, 0, 255));
                        centralGraphics.setCharacter(x, y, '~');

                    } else if (intColor < ground) {
                        centralGraphics.setForegroundColor(new TextColor.RGB(0, 255, 0));
                        centralGraphics.setCharacter(x, y, ' ');

                    } else if (intColor < mountain) {
                        centralGraphics.setForegroundColor(new TextColor.RGB(255, 0, 0));
                        centralGraphics.setCharacter(x, y, '#');
                    }
                }
            }
            centralGraphics.putString(0, 31,
                    "" + "ground:" + ground + " mountain:" + mountain + " water:" + water + " octaves:" + octaves);

            try {
                this.screen.refresh();
                Thread.yield();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }

            KeyStroke keyStroke = terminal.pollInput();
            if (keyStroke != null) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    break;
                }
                if (keyStroke.getKeyType() == KeyType.ArrowLeft) {
                    col -= 20;
                }
                if (keyStroke.getKeyType() == KeyType.ArrowRight) {
                    col += 20;
                }
                if (keyStroke.getKeyType() == KeyType.ArrowUp) {
                    row -= 20;
                }
                if (keyStroke.getKeyType() == KeyType.ArrowDown) {
                    row += 20;
                }
                if (keyStroke.getKeyType() == KeyType.Character) {
                    if (keyStroke.getCharacter() == '1') {
                        ground++;
                    }
                    if (keyStroke.getCharacter() == '2') {
                        ground--;
                    }
                    if (keyStroke.getCharacter() == '3') {
                        water++;
                    }
                    if (keyStroke.getCharacter() == '4') {
                        water--;
                    }
                    if (keyStroke.getCharacter() == '5') {
                        mountain++;
                    }
                    if (keyStroke.getCharacter() == '6') {
                        mountain--;
                    }
                    if (keyStroke.getCharacter() == '7') {
                        octaves++;
                    }
                    if (keyStroke.getCharacter() == '8') {
                        octaves--;
                    }
                }

            }

        }
    }

    float scaleAndShift(float value, float inMin, float inMax, float outMin, float outMax) {
        return ((value - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
    }

}
