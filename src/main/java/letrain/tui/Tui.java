package letrain.tui;

public class Tui implements SimpleUI {
    public static final int ROWS = 25;
    public static final int COLS = 80;
    char[][] screen = new char[COLS][ROWS];

    public Tui() {
        clear();
        fill(0, 0, COLS , ROWS , ' ');
    }

    @Override
    public void paint() {
        System.out.println();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                System.out.print(screen[col][row]);
            }
            System.out.println();
        }
    }

    @Override
    public void clear() {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                screen[col][row] = ' ';
            }
        }
    }

    @Override
    public void set(int x, int y, char c) {
        screen[x][y] = c;
    }

    @Override
    public void clear(int x, int y) {
        screen[x][y] = ' ';
    }

    @Override
    public void fill(int x, int y, int width, int height, char c) {
        for (int col = x; col < x + width; col++) {
            for (int row = y; row < y + height; row++) {
                set(col, row, c);
            }
        }
    }

    @Override
    public void box(int x, int y, int width, int height) {
         fill(x,y,width,1,(char)'-');
         fill(x,y+height,width,1,(char)'-');
         fill(x,y,1,height,(char)'|');
         fill(x+width,y,1, height,(char)'|');
         set(x,y,'+');
         set(x,y+height,'+');
         set(x+width,y,'+');
         set(x+width,y+height,'+');

    }
}
