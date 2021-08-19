package letrain.physics;

import letrain.map.Dir;

import java.util.Objects;

public class Vector2D {
    public final double x;
    public final double y;

    public Vector2D(Vector2D vectorXY){
        this(vectorXY.x, vectorXY.y);
    }
    public Vector2D() {
        this(0,0);
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
        return (double) Math.sqrt(x * x+ y *y);
    }

    public Vector2D add(Vector2D v) {
        return new Vector2D(this.x + v.x, this.y + v.y);
    }

    public Vector2D addX(double x) {
        return new Vector2D(this.x+x, y);
    }

    public Vector2D addY(double y) {
        return new Vector2D(x, this.y + y);
    }

    public Vector2D sub(Vector2D v) {
        return new Vector2D(this.x-v.x, this.y-v.y);
    }

    public Vector2D mult(double n) {
        return new Vector2D(this.x*n, this.y*n);
    }

    public Vector2D scale(double value){
        return mult(value);
    }

    public Vector2D mult(Vector2D v) {
        return new Vector2D(this.x*v.x, this.y*v.y);
    }
    public Vector2D div(double n) {
        return new Vector2D(this.x/n, this.y/n);
    }
    public Vector2D div(Vector2D v) {
        return new Vector2D(this.x/v.x, this.y/v.y);
    }

    public double distance(Vector2D v) {
        double dx = x - v.x;
        double dy = y - v.y;
        int sign = 1;
        if(Math.signum(dx*dy) == -1){
            sign = -1;
        }
        return  Math.sqrt(Math.abs(dx * dy)) *sign;
    }

    public double dot(Vector2D v) {
        return x * v.x + y * v.y;
    }

    public double dot(double value) {
        return this.x * value + y * value;
    }

    public Vector2D normalize() {
        double m = magnitude();
        if (m > 0) {
            return new Vector2D(x/m, y/m);
        } else {
            return new Vector2D(m, m);
        }
    }

    public Vector2D limit(double max) {
        if (magnitude() > max) {
            return new Vector2D(x,y).normalize().mult(max);
        }
        return this;
    }

    public double angle() {
        double angle = Math.atan2(y, x);
        return (double) angle;
    }

    public double clampRadians(double radians) {
        return (double) (radians % (Math.PI * 2));
    }

    public Vector2D rotate(double radians){
        double cr = Math.cos(radians);
        double sr = Math.sin(radians);
        return new Vector2D((cr*x - sr*y), (sr*x + cr*y));
    }
    public static Vector2D fromPolar(double radians, double module){
        double x = Math.cos(radians) * module;
        double y = Math.sin(radians) * module;
        return new Vector2D((double)x,(double)y);
    }
    public static Vector2D fromDir(Dir dir, double module){
        double degrees = dir.getValue() * 45;
        double radians = toRadians(degrees);
        return fromPolar(radians, module);
    }
    public Dir toDir(){
        return Dir.fromInt((int) cardinal(angle(), 8));
    }
    public String toString() {
        return "[" +String.format("%.2f", x) + ", "+ String.format("%.2f",y) +"]";
    }

    // STATIC /////////////////////////////////////
    public static double dot(Vector2D v1, Vector2D v2) {
        return v1.x * v2.x + v1.y * v2.y;
    }
    public static Vector2D div(Vector2D v1, Vector2D v2) {
        return div(v1, v2);
    }
    public static Vector2D mult(Vector2D v, double n) {
        return mult(v, n);
    }
    public static Vector2D add(Vector2D v1, Vector2D v2) {
        return add(v1, v2);
    }
    public static Vector2D sub(Vector2D v1, Vector2D v2) {
        return sub(v1, v2);
    }
    public static Vector2D mult(Vector2D v1, Vector2D v2) {
        return mult(v1, v2);
    }
    public static Vector2D div(Vector2D v, double n) {
        return div(v, n);
    }
    public static double distance(Vector2D v1, Vector2D v2) {
        double dx = v1.x - v2.x;
        double dy = v1.y - v2.y;
        return (double) Math.sqrt(dx *dx + dy * dy);
    }
    public static double toRadians(double degrees) {
        return (double) Math.toRadians(degrees);
    }
    public static double toDegrees(double radians) {
        return (double) Math.toDegrees(radians);
    }
    public static double angleBetween(Vector2D v1, Vector2D v2) {
        double arc = (v1.x * v1.x + v1.y * v1.y) * (v2.x * v2.x + v2.y * v2.y);
        arc = (double) Math.sqrt(arc);
        if (arc > 0) {
            arc = (double) Math.acos((v1.x * v2.x + v1.y * v2.y) / arc);
            if (v1.x * v2.y - v1.y * v2.x < 0) {
                arc = -arc;
            }
        }
        return arc;
    }
    public static double simpleAngleBetween(Vector2D v1, Vector2D v2) {
        double a = Math.atan2(v2.y, v2.x) - Math.atan2(v1.y, v1.x);
        return (double) ((a + Math.PI) % (Math.PI * 2) - Math.PI);
    }
    public static double cardinal(double angle, int steps){
        return Math.floor(steps*angle/(2*Math.PI)+steps +0.5)%steps;
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
}