package letrain.tui;

public interface SimpleUI {
    void paint();
    public void clear();
    public void set(int x, int y, String c);
    public void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);
}
