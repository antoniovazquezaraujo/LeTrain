package letrain.map;

public interface Rotable {
    void rotateLeft();

    void rotateLeft(int angle);

    void rotateRight();

    void rotateRight(int angle);

    void rotate(int angle);

    Dir getDir();

    void setDir(Dir dir);

    boolean reverse();

    boolean isReversed();
}
