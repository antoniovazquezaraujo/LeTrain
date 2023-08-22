package letrain.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PointTest {

    @Test
    public void testEquals() {
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);
        Point p3 = new Point(3, 4);

        Assertions.assertEquals(p1, p2);
        Assertions.assertNotEquals(p1, p3);
    }

    @Test
    public void testLocate() {
        Point p1 = new Point(1, 1);
        Point p2 = new Point(2, 2);

        Assertions.assertEquals(Dir.SE, p1.locate(p2));
        Assertions.assertEquals(Dir.NW, p2.locate(p1));
    }

    @Test
    public void testDistance() {
        Point p1 = new Point(1, 1);
        Point p2 = new Point(4, 5);

        Assertions.assertEquals(5.0, Point.distance(p1, p2), 0.01);
    }

    @Test
    public void testGetPage() {
        Point p1 = new Point(1, 1);
        Point p2 = new Point(-1, -1);

        Assertions.assertEquals(new Page(0, 0), p1.getPage());
        Assertions.assertEquals(new Page(-1, -1), p2.getPage());

        Assertions.assertEquals(new Page(0, 0), new Point(0, 0).getPage());
        Assertions.assertEquals(new Page(0, 0), new Point(0, 1).getPage());
        Assertions.assertEquals(new Page(0, 0), new Point(1, 0).getPage());
        Assertions.assertEquals(new Page(0, 0), new Point(1, 1).getPage());

        Assertions.assertEquals(new Page(0, -1), new Point(0, -1).getPage());
        Assertions.assertEquals(new Page(-1, 0), new Point(-1, 0).getPage());
        Assertions.assertEquals(new Page(-1, -1), new Point(-1, -1).getPage());

    }

    @Test
    public void testSetPage() {
        // Page.setHeight(10);
        // Page.setWidth(10);
        // Point p1 = new Point(1, 1);
        // Point p2 = new Point(-1, -1);
        // Page page1 = new Page(2, 3);
        // Page page2 = new Page(-1, 0);

        // Assertions.assertEquals(new Point(21, 31), p1.setPage(page1));
        // Assertions.assertEquals(new Point(-1, 9), p2.setPage(page2));
        Page.setHeight(3);
        Page.setWidth(3);
        Assertions.assertEquals(new Point(1, 1), new Point(1, 1).setPage(new Page(0, 0)));
        Assertions.assertEquals(new Point(4, 4), new Point(1, 1).setPage(new Page(1, 1)));
        Assertions.assertEquals(new Point(-2, -2), new Point(1, 1).setPage(new Page(-1, -1)));
        Assertions.assertEquals(new Point(0, 0), new Point(0, 0).setPage(new Page(0, 0)));
        Assertions.assertEquals(new Point(3, -3), new Point(0, 0).setPage(new Page(1, -1)));
        Assertions.assertEquals(new Point(30, 0), new Point(0, 0).setPage(new Page(10, 0)));
        Assertions.assertEquals(new Point(31, 0), new Point(1, 0).setPage(new Page(10, 0)));

    }

    @Test
    public void testGetPosInPage() {
        Page.setHeight(10);
        Page.setWidth(10);
        Point p1 = new Point(1, 1);
        Point p2 = new Point(-1, -1);

        Assertions.assertEquals(new Point(1, 1), p1.getPosInPage());
        Assertions.assertEquals(new Point(9, 9), p2.getPosInPage());
    }

    @Test
    public void testMove() {
        Point p1 = new Point(1, 1);

        p1.move(Dir.E);
        Assertions.assertEquals(new Point(2, 1), p1);

        p1.move(Dir.S);
        Assertions.assertEquals(new Point(2, 2), p1);

        p1.move(Dir.NW);
        Assertions.assertEquals(new Point(1, 1), p1);
    }

    @Test
    public void testMoveWithDistance() {
        Point p1 = new Point(1, 1);

        p1.move(Dir.E, 2);
        Assertions.assertEquals(new Point(3, 1), p1);

        p1.move(Dir.S, 3);
        Assertions.assertEquals(new Point(3, 4), p1);

        p1.move(Dir.NW, 2);
        Assertions.assertEquals(new Point(1, 2), p1);
    }

    @Test
    public void testMoveByPagesWithDistance() {
        Page.setWidth(5);
        Page.setHeight(5);
        Point p1 = new Point(1, 1);

        Assertions.assertEquals(new Point(26, 26), p1.moveByPages(Dir.SE, 5));
        p1 = new Point(1, 1);
        Assertions.assertEquals(new Point(1, 1), p1.moveByPages(0, 0));
        p1 = new Point(1, 1);
        Assertions.assertEquals(new Point(-4, -4), p1.moveByPages(-1, -1));

    }

    @Test
    public void testMoveByPagesWithOffset() {
        Page.setWidth(10);
        Page.setHeight(10);
        Point p1 = new Point(1, 1);

        Assertions.assertEquals(new Point(21, 31), p1.moveByPages(2, 3));
        Assertions.assertEquals(new Point(-9, -19), p1.moveByPages(-1, -2));
        Assertions.assertEquals(new Point(1, 1), p1.moveByPages(0, 0));
    }
}