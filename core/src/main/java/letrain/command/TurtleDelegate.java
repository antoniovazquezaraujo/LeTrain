package letrain.command;

public interface TurtleDelegate {
    void startSequence();
    void moveForward();
    void buildForward();
    void eraseForward();
    void turnLeft();
    void turnRight();
    void endSequence();
}
