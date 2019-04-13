package letrain.tui;

public interface SimpleUI {
    void paint();
    public void clear();
    public void set(int x, int y, char c);
    public void clear(int x, int y);

    void fill(int x, int y, int width, int height, char c);

    void box(int x, int y, int width, int height);
}
