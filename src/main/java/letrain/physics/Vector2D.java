package letrain.physics;

import letrain.map.Dir;

import java.util.Objects;

public class Vector2D {
    public double x;
    public double y;

    public Vector2D(Vector2D vectorXY) {
        this(vectorXY.x, vectorXY.y);
    }

    public Vector2D() {
        this(0, 0);
    }

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }

    public void add(Vector2D v) {
        this.x += v.x;
        this.y += v.y;
    }

    public void sub(Vector2D v) {
        this.x -= v.x;
        this.y -= v.y;
    }
    public void mult(double n) {
        this.x *= n;
        this.y *= n;
    }

    public void mult(Vector2D v) {
        this.x *= v.x;
        this.y *= v.y;
    }

    public void div(double n) {
        this.x /= n;
        this.y /= n;
    }

    public void div(Vector2D v) {
        this.x /= v.x;
        this.y /= v.y;
    }

//    public double distance(Vector2D v) {
//        double dx = x - v.x;
//        double dy = y - v.y;
//        int sign = 1;
//        if (dx <0 ^ dy <0){
////        if (Math.signum(dx * dy) == -1) {
//            sign = -1;
//        }
//        return Math.sqrt(Math.abs(dx * dy)) * sign;
//    }

    public double dot(Vector2D v) {
        return x * v.x + y * v.y;
    }

    public double dot(double value) {
        return this.x * value + y * value;
    }

    public void normalize() {
        double m = magnitude();
        if (m != 0) {
            this.x /= m;
            this.y /= m;
        } else {
            this.x = 1;
            this.y = 1;
        }
    }

    public void limit(double max) {
        if (magnitude() > max) {
            normalize();
            mult(max);
        }
    }

    public double angleInRadians() {
        double angle = Math.atan2(y, x);
        return angle;
    }

    public double clampRadians(double radians) {
        return radians % (Math.PI * 2);
    }

    public void rotate(double radians) {
        double cr = Math.cos(radians);
        double sr = Math.sin(radians);
        this.x = (cr * x - sr * y);
        this.y = (sr * x + cr * y);
    }

    public static Vector2D fromPolar(double radians, double module) {
        double x = Math.cos(radians) * module;
        double y = Math.sin(radians) * module;
        return new Vector2D(x, y);
    }

    public static Vector2D fromDir(Dir dir, double module) {
        double degrees = dir.getValue() * 45;
        double radians = degreesToRadians(degrees);
        return fromPolar(radians, module);
    }

    public void move(Dir dir, int distance) {

        switch (dir) {
            case N:
                y += distance;
                break;
            case NE:
                y += distance;
                x += distance;
                break;
            case E:
                x += distance;
                break;
            case SE:
                y -= distance;
                x += distance;
                break;
            case S:
                y -= distance;
                break;
            case SW:
                y -= distance;
                x -= distance;
                break;
            case W:
                x -= distance;
                break;
            case NW:
                y += distance;
                x -= distance;
                break;
            default:
                assert (false);
        }
    }
    public Dir locate( Vector2D newPos){
        Vector2D copy = new Vector2D(newPos);
        copy.sub(this);
        return copy.toDir();
    }
    public Dir toDir() {
        return Dir.fromInt(radiansToCardinal(angleInRadians()));
    }

    public String toString() {
        return "[" + String.format("%.2f", x) + ", " + String.format("%.2f", y) + "]";
    }

    // STATIC /////////////////////////////////////
    public static double dot(Vector2D v1, Vector2D v2) {
        return v1.x * v2.x + v1.y * v2.y;
    }

    public static Vector2D div(Vector2D v1, Vector2D v2) {
        Vector2D ret = new Vector2D(v1);
        ret.div(v2);
        return ret;
    }

    public static Vector2D div(Vector2D v, double n) {
        Vector2D ret = new Vector2D(v);
        ret.div(n);
        return ret;
    }

    public static Vector2D mult(Vector2D v, double n) {
        Vector2D ret = new Vector2D(v);
        ret.mult(n);
        return ret;
    }

    public static Vector2D mult(Vector2D v1, Vector2D v2) {
        Vector2D ret = new Vector2D(v1);
        ret.mult(v2);
        return ret;
    }

    public static Vector2D add(Vector2D v1, Vector2D v2) {
        Vector2D ret = new Vector2D(v1);
        ret.add(v2);
        return ret;
    }
    public static Vector2D sub(Vector2D v1, Vector2D v2) {
        Vector2D ret = new Vector2D(v1);
        ret.sub(v2);
        return ret;
    }
    public static double distance(Vector2D v1, Vector2D v2) {
        double dx = v1.x - v2.x;
        double dy = v1.y - v2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double degreesToRadians(double degrees) {
        return Math.toRadians(degrees);
    }

    public static double radiansToDegrees(double radians) {
        return Math.toDegrees(radians);
    }

    public static double angleBetween(Vector2D v1, Vector2D v2) {
        double arc = (v1.x * v1.x + v1.y * v1.y) * (v2.x * v2.x + v2.y * v2.y);
        arc = Math.sqrt(arc);
        if (arc > 0) {
            arc = Math.acos((v1.x * v2.x + v1.y * v2.y) / arc);
            if (v1.x * v2.y - v1.y * v2.x < 0) {
                arc = -arc;
            }
        }
        return arc;
    }

    public static double simpleAngleBetween(Vector2D v1, Vector2D v2) {
        double a = Math.atan2(v2.y, v2.x) - Math.atan2(v1.y, v1.x);
        return ((a + Math.PI) % (Math.PI * 2) - Math.PI);
    }

    public static int degreesToCardinal(double degrees) {
        if (degrees < 0) {
            degrees += 360;
        }
        return (int) Math.floor(((degrees + 22.5) % 360) / 45);
    }

    public static int radiansToCardinal(double angleInRadians) {
        double degrees = Math.toDegrees(angleInRadians);
        return degreesToCardinal(degrees);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector2D vector2D = (Vector2D) o;
        return Double.compare(vector2D.x, x) == 0 && Double.compare(vector2D.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public boolean almostEquals(Vector2D vector2D) {
        return Double.compare(vector2D.x, x) == 0 && Double.compare(vector2D.y, y) == 0;
    }

    public void round() {
        this.x = Math.round(this.x);
        this.y = Math.round(this.y);
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }
}