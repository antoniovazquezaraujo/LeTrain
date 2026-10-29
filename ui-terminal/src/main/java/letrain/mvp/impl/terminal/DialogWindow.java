package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.screen.Screen;

/**
 * Shared chrome of the two centered 2D dialogs (New Game and the in-game Settings, #742 polish).
 * Both float over the running game like their 3D counterparts: a bounded content box centered on
 * the terminal, framed by Lanterna's one-line decoration (the title centered on the top border) and
 * with the last game frame as the GUI backdrop.
 *
 * <p>
 * Lanterna's {@code AbstractBasePane} always fills the screen with spaces before drawing the
 * background component, so a transparent backdrop cannot keep the map. Instead the screen's front
 * buffer (the last {@code TerminalView.paint()} + {@code refresh()}) is copied into a
 * {@link FrozenBackground} and repainted on every GUI update. The game is frozen while the modal
 * dialog runs, so this static snapshot stays valid.
 *
 * <p>
 * The geometry is pure maths ({@link #dialogSize(TerminalSize)}) and accounts for the decoration:
 * the returned size is the <em>content</em> box, while the window manager centers and clamps the
 * decorated box (one cell per side). This keeps the frame outside the usable content, so no inner
 * row is lost.
 */
final class DialogWindow {

    /** Cells the one-line window decoration adds on each side. */
    static final int DECORATION_COLUMNS = 1;
    static final int DECORATION_ROWS = 1;
    /** Margins around the decorated dialog, so the game shows on every side. */
    static final int MARGIN_COLUMNS = 2;
    static final int MARGIN_ROWS = 1;
    /** Narrowest content box: the five Settings footer buttons in one row plus a little slack. */
    static final int MIN_COLUMNS = 70;
    /** Widest content box, so large terminals keep the game around the dialog. */
    static final int MAX_COLUMNS = 100;
    /** Shortest content box: tabs, a usable section body and the three footer rows. */
    static final int MIN_ROWS = 20;
    /** Tallest content box, keeping a bounded dialog on tall terminals. */
    static final int MAX_ROWS = 30;

    /**
     * Content size of the centered dialog for a terminal. The available area discounts the
     * decoration (one cell per side) and the margins (2 columns / 1 row); the width is clamped
     * between {@link #MIN_COLUMNS} and {@link #MAX_COLUMNS} and never exceeds the available area,
     * the height between {@link #MIN_ROWS} and {@link #MAX_ROWS} and never exceeds the available
     * area either. The result is always positive.
     */
    static TerminalSize dialogSize(TerminalSize screen) {
        return dialogSize(screen, MIN_COLUMNS, MAX_COLUMNS);
    }

    /** Same size calculation with an explicit width range for wider dialogs (Works). */
    static TerminalSize dialogSize(TerminalSize screen, int minColumns, int maxColumns) {
        TerminalSize available = availableContent(screen, MARGIN_COLUMNS, MARGIN_ROWS);
        int width = Math.max(minColumns, screen.getColumns() / 2);
        width = Math.min(width, Math.min(maxColumns, available.getColumns()));

        int height = Math.min(Math.max(MIN_ROWS, available.getRows()), MAX_ROWS);
        height = Math.min(height, available.getRows());

        return new TerminalSize(Math.max(1, width), Math.max(1, height));
    }

    /**
     * Content area left after the decoration and the given margins; never smaller than 1x1 (a real
     * terminal never reports 0x0, but the pure function stays well defined).
     */
    static TerminalSize availableContent(TerminalSize screen, int marginColumns, int marginRows) {
        int columns = Math.max(1, screen.getColumns() - 2 * DECORATION_COLUMNS - 2 * marginColumns);
        int rows = Math.max(1, screen.getRows() - 2 * DECORATION_ROWS - 2 * marginRows);
        return new TerminalSize(columns, rows);
    }

    /**
     * GUI for a modal dialog over the running game: the dark dialog theme plus a backdrop that
     * repaints the frame visible when the dialog opened.
     */
    static MultiWindowTextGUI gui(Screen screen) {
        MultiWindowTextGUI gui =
                new MultiWindowTextGUI(screen, new DefaultWindowManager(), null, snapshot(screen));
        gui.setTheme(NewGameDialog.darkTheme(gui.getTheme()));
        return gui;
    }

    /**
     * GUI with the same default palette as the program editor dialog ({@code showIDE}), plus the
     * frozen game backdrop. The Works list uses it so section labels and disabled controls keep the
     * editor's readable colours instead of the dark dialog theme.
     */
    static MultiWindowTextGUI programGui(Screen screen) {
        MultiWindowTextGUI gui =
                new MultiWindowTextGUI(screen, new DefaultWindowManager(), null, snapshot(screen));
        gui.setTheme(TerminalView.centeredTitleTheme(gui.getTheme()));
        return gui;
    }

    /** Copy of the screen's front buffer (the last painted frame) for use as a GUI backdrop. */
    static FrozenBackground snapshot(Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        TextCharacter[][] cells = new TextCharacter[size.getRows()][size.getColumns()];
        for (int row = 0; row < size.getRows(); row++) {
            for (int column = 0; column < size.getColumns(); column++) {
                TextCharacter character = screen.getFrontCharacter(column, row);
                cells[row][column] = character == null ? Screen.DEFAULT_CHARACTER : character;
            }
        }
        return new FrozenBackground(cells);
    }

    /**
     * Backdrop that repaints a captured screen: the running map stays visible around the centered
     * dialog. Cells outside the captured area keep the theme backdrop.
     */
    static final class FrozenBackground extends AbstractComponent<FrozenBackground> {

        private final TextCharacter[][] cells;

        FrozenBackground(TextCharacter[][] cells) {
            this.cells = cells;
        }

        @Override
        protected ComponentRenderer<FrozenBackground> createDefaultRenderer() {
            return new Renderer();
        }

        private static final class Renderer implements ComponentRenderer<FrozenBackground> {
            @Override
            public TerminalSize getPreferredSize(FrozenBackground component) {
                return TerminalSize.ONE;
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, FrozenBackground component) {
                int rows = Math.min(graphics.getSize().getRows(), component.cells.length);
                for (int row = 0; row < rows; row++) {
                    int columns =
                            Math.min(graphics.getSize().getColumns(), component.cells[row].length);
                    for (int column = 0; column < columns; column++) {
                        graphics.setCharacter(column, row, component.cells[row][column]);
                    }
                }
            }
        }
    }

    private DialogWindow() {}
}
