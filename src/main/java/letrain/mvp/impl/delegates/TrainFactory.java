package letrain.mvp.impl.delegates;

import letrain.map.Point;

public class TrainFactory {
    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position = position;
    }

    Point position;
}
