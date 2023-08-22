package letrain.mvp.impl.delegates;

import java.io.Serializable;

import letrain.map.Point;

public class TrainFactory implements Serializable {
    Point position = new Point(0, 0);

    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position.setX(position.getX());
        this.position.setY(position.getY());
    }
}
