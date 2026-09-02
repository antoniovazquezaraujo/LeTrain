package letrain.command;

public interface TurtleDelegate {
    void moveForward();
    void buildForward();
    void eraseForward();
    void turnLeft();
    void turnRight();
    void endSequence();
}
