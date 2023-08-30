package letrain.map;

import static letrain.map.Dir.E;
import static letrain.map.Dir.N;
import static letrain.map.Dir.NE;
import static letrain.map.Dir.NW;
import static letrain.map.Dir.S;
import static letrain.map.Dir.SE;
import static letrain.map.Dir.SW;
import static letrain.map.Dir.W;

import java.io.Serializable;

public class Point implements Serializable {
    private static final long serialVersionUID = 1L;

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
        } else {// y == p.y
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
        return "" + x + "," + y;
    }

    public void move(Dir dir) {
        move(dir, 1);
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

    public static double distance(Point from, Point to) {
        return Math.sqrt(Math.pow((to.getX() - from.getX()), 2) + Math.pow((to.getY() - from.getY()), 2));
    }

    public Page getPage() {
        int pageX = (getX() < 0) ? (getX() + 1) / Page.getWidth() - 1 : getX() / Page.getWidth();
        int pageY = (getY() < 0) ? (getY() + 1) / Page.getHeight() - 1 : getY() / Page.getHeight();
        return new Page(pageX, pageY);
    }

    public Point addPage(Page page) {
        return new Point(
                (getX() + page.getX() * Page.getWidth()),
                (getY() + page.getY() * Page.getHeight()));
    }

    public Point setPage(Page page) {
        Point relativePosition = this.getPosInPage();
        return relativePosition.addPage(page);
    }

    public Point getPosInPage() {
        Page currentPage = getPage();
        int relativeX = getX() - currentPage.getX() * Page.getWidth();
        int relativeY = getY() - currentPage.getY() * Page.getHeight();
        return new Point(relativeX, relativeY);
    }

    public Point moveByPages(Dir dir) {
        return this.moveByPages(dir, 1);
    }

    public Point moveByPages(Dir dir, int distance) {
        int xOffset = 0;
        int yOffset = 0;
        switch (dir) {
            case N:
                yOffset = -distance;
                break;
            case NE:
                yOffset = -distance;
                xOffset = distance;
                break;
            case E:
                xOffset = distance;
                break;
            case SE:
                yOffset = distance;
                xOffset = distance;
                break;
            case S:
                yOffset = distance;
                break;
            case SW:
                yOffset = distance;
                xOffset = -distance;
                break;
            case W:
                xOffset = -distance;
                break;
            case NW:
                yOffset = -distance;
                xOffset = -distance;
                break;
            default:
                assert (false);
        }
        return moveByPages(xOffset, yOffset);
    }

    public Point moveByPages(int xOffset, int yOffset) {
        int newX = getX() + (xOffset * Page.getWidth());
        int newY = getY() + (yOffset * Page.getHeight());
        return new Point(newX, newY);
    }

}
