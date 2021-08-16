package letrain.physics;

import letrain.map.Dir;

public class VectorXY {
    public double x;
    public double y;

    public VectorXY(VectorXY vectorXY){
        this(vectorXY.x, vectorXY.y);
    }
    public VectorXY() {
        this(0,0);
    }

    public VectorXY(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void set(VectorXY v) {
        x = v.x;
        y = v.y;
    }
    public void reset(){
        x = 0;
        y = 0;
    }

    public void setX(double source) {
        x = source;
    }

    public void setY(double source) {
        y = source;
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

    public void add(VectorXY v) {
        x += v.x;
        y += v.y;
    }

    public void addX(double x) {
        this.x += x;
    }

    public void addY(double y) {
        this.y += y;
    }

    public void sub(VectorXY v) {
        x -= v.x;
        y -= v.y;
    }

    public void multX(double n) {
        x *= n;
    }

    public void multY(double n) {
        y *= n;
    }

    public void mult(double n) {
        x *= n;
        y *= n;
    }

    public void scale(double value){
        mult(value);
    }

    public void mult(VectorXY v) {
        x *= v.x;
        y *= v.y;
    }
    public void div(double n) {
        x /= n;
        y /= n;
    }
    public void div(VectorXY v) {
        x /= v.x;
        y /= v.y;
    }

    public double distance(VectorXY v) {
        double dx = x - v.x;
        double dy = y - v.y;
        return (double) Math.sqrt(dx * dy);
    }

    public double dot(VectorXY v) {
        return x * v.x + y * v.y;
    }

    public double dot(double value) {
        return this.x * value + y * value;
    }

    public boolean isAlmostTheSameSense(VectorXY v1, VectorXY v2){
        return Math.acos(dot(v1, v2)) > 0;
    }

    public void normalize() {
        double m = magnitude();
        if (m != 0 && m != 1) {
            div(m);
        }
    }

    public VectorXY normalize(VectorXY target) {
        if (target == null) {
            target = new VectorXY();
        }
        double m = magnitude();
        if (m > 0) {
            target.setX(x / m);
            target.setY(y / m);
        } else {
            target.setX(m);
            target.setY(m);
        }
        return target;
    }

    public void limit(double max) {
        if (magnitude() > max) {
            normalize();
            mult(max);
        }
    }

    public double angle() {
        double angle = Math.atan2(y, x);
        return (double) angle;
    }

    public double clampRadians(double radians) {
        return (double) (radians % (Math.PI * 2));
    }

    public void rotate(double radians){
        double cr = Math.cos(radians);
        double sr = Math.sin(radians);
        setX((double) (cr*x - sr*y));
        setY((double) (sr*x + cr*y));
    }
    public static VectorXY fromPolar(double radians, double module){
        double x = Math.cos(radians) * module;
        double y = Math.sin(radians) * module;
        return new VectorXY((double)x,(double)y);
    }
    public static VectorXY fromDir(Dir dir, double module){
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
    public static double dot(VectorXY v1, VectorXY v2) {
        return v1.x * v2.x + v1.y * v2.y;
    }
    public static VectorXY div(VectorXY v1, VectorXY v2) {
        return div(v1, v2, null);
    }
    public static VectorXY mult(VectorXY v, double n) {
        return mult(v, n, null);
    }
    public static VectorXY add(VectorXY v1, VectorXY v2) {
        return add(v1, v2, null);
    }
    public static VectorXY add(VectorXY v1, VectorXY v2, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v1.x + v2.x, v1.y + v2.y);
        } else {
            target.setX(v1.x + v2.x);
            target.setY(v1.y + v2.y);
        }
        return target;
    }
    public static VectorXY sub(VectorXY v1, VectorXY v2) {
        return sub(v1, v2, null);
    }
    public static VectorXY sub(VectorXY v1, VectorXY v2, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v1.x - v2.x, v1.y - v2.y);
        } else {
            target.setX(v1.x - v2.x);
            target.setY(v1.y - v2.y);
        }
        return target;
    }
    public static VectorXY mult(VectorXY v, double n, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v.x * n, v.y * n);
        } else {
            target.setX(v.x * n);
            target.setY(v.y * n);
        }
        return target;
    }
    public static VectorXY mult(VectorXY v1, VectorXY v2) {
        return mult(v1, v2, null);
    }
    public static VectorXY mult(VectorXY v1, VectorXY v2, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v1.x * v2.x, v1.y * v2.y);
        } else {
            target.setX(v1.x * v2.x);
            target.setY(v1.y * v2.y);
        }
        return target;
    }
    public static VectorXY div(VectorXY v, double n) {
        return div(v, n, null);
    }
    public static VectorXY div(VectorXY v, double n, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v.x / n, v.y / n);
        } else {
            target.setX(v.x / n);
            target.setY(v.y / n);
        }
        return target;
    }
    public static VectorXY div(VectorXY v1, VectorXY v2, VectorXY target) {
        if (target == null) {
            target = new VectorXY(v1.x / v2.x, v1.y / v2.y);
        } else {
            target.setX(v1.x / v2.x);
            target.setY(v1.y / v2.y);
        }
        return target;
    }
    public static double distance(VectorXY v1, VectorXY v2) {
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
    public static double angleBetween(VectorXY v1, VectorXY v2) {
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

    public static double simpleAngleBetween(VectorXY v1, VectorXY v2) {
        double a = Math.atan2(v2.y, v2.x) - Math.atan2(v1.y, v1.x);
        return (double) ((a + Math.PI) % (Math.PI * 2) - Math.PI);
    }

    public static double cardinal(double angle, int steps){
        return Math.floor(steps*angle/(2*Math.PI)+steps +0.5)%steps;
    }
}