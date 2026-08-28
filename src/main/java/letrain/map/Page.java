package letrain.map;

public class Page {
    private int x;
    private int y;
    static int width = 80;
    static int height = 24;

    public Page(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Page other = (Page) obj;
        if (x != other.x)
            return false;
        if (y != other.y)
            return false;
        return true;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public static int getWidth() {
        return width;
    }

    public static void setWidth(int width) {
        Page.width = width;
    }

    public static int getHeight() {
        return height;
    }

    public static void setHeight(int height) {
        Page.height = height;
    }

    @Override
    public String toString() {
        return "Page [x=" + x + ", y=" + y + "]";
    }
}
