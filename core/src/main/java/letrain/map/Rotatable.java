package letrain.map;

public interface Rotatable {
    void rotateLeft();

    void rotateLeft(int angle);

    void rotateRight();

    void rotateRight(int angle);

    void rotate(int angle);

    Dir getDir();

    void setDir(Dir dir);
}
