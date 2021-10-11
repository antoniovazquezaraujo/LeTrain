package letrain.map;

import java.io.Serializable;

import static letrain.map.Dir.*;

public class Point implements Serializable {
    private int x;
    private int y;

    public Point(int x, int y) {
        this.y = y;
        this.x = x;
    }

    @Override
    public boolean equals(Object p) {
        if (p.getClass() != Point.class) {
            return false;
        }
        Point q = (Point) p;
        return q.y == this.y && q.x == this.x;
    }

    public Point(Point pos) {
        this(pos.x, pos.y);
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public Dir locate(Point p) {
        if (y > p.y) {
            if (x > p.x) {
                return NW;
            } else if (x < p.x) {
                return NE;
            } else {
                return N;
            }
        } else if (y < p.y) {
            if (x > p.x) {
                return SW;
            } else if (x < p.x) {
                return SE;
            } else {
                return S;
            }
        } else {//y == p.y
            if (x > p.x) {
                return W;
            } else if (x < p.x) {
                return E;
            } else {
                return null;
            }
        }
    }
    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
    public void move(Dir dir) {
        move(dir, 1);
    }

    public Point copy(Dir dir) {
        Point p = this;
        p.move(dir);
        return p;
    }

    public void move(Dir dir, int distance) {

        switch (dir) {
            case N:
                y -= distance;
                break;
            case NE:
                y -= distance;
                x += distance;
                break;
            case E:
                x += distance;
                break;
            case SE:
                y += distance;
                x += distance;
                break;
            case S:
                y += distance;
                break;
            case SW:
                y += distance;
                x -= distance;
                break;
            case W:
                x -= distance;
                break;
            case NW:
                y -= distance;
                x -= distance;
                break;
            default:
                assert (false);
        }
    }
}
