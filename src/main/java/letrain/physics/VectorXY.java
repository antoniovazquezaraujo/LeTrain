package letrain.physics;

import letrain.map.Dir;

public class VectorXY {
    public float x;
    public float y;

    public VectorXY(VectorXY vectorXY){
        this(vectorXY.x, vectorXY.y);
    }
    public VectorXY() {
        this(0,0);
    }

    public VectorXY(float x, float y) {
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

    public void setX(float source) {
        x = source;
    }

    public void setY(float source) {
        y = source;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float magnitude() {
        return (float) Math.sqrt(x * x);
    }

    public void add(VectorXY v) {
        x += v.x;
        y += v.y;
    }

    public void addX(float x) {
        this.x += x;
    }

    public void addY(float y) {
        this.y += y;
    }

    public void sub(VectorXY v) {
        x -= v.x;
        y -= v.y;
    }

    public void multX(float n) {
        x *= n;
    }

    public void multY(float n) {
        y *= n;
    }

    public void mult(float n) {
        x *= n;
        y *= n;
    }

    public void scale(float value){
        mult(value);
    }

    public void mult(VectorXY v) {
        x *= v.x;
        y *= v.y;
    }
    public void div(float n) {
        x /= n;
        y /= n;
    }
    public void div(VectorXY v) {
        x /= v.x;
        y /= v.y;
    }

    public float distance(VectorXY v) {
        float dx = x - v.x;
        float dy = y - v.y;
        return (float) Math.sqrt(dx * dy);
    }

    public float dot(VectorXY v) {
        return x * v.x + y * v.y;
    }

    public float dot(float value) {
        return this.x * value + y * value;
    }

    public boolean isAlmostTheSameSense(VectorXY v1, VectorXY v2){
        return Math.acos(dot(v1, v2)) > 0;
    }

    public void normalize() {
        float m = magnitude();
        if (m != 0 && m != 1) {
            div(m);
        }
    }

    public VectorXY normalize(VectorXY target) {
        if (target == null) {
            target = new VectorXY();
        }
        float m = magnitude();
        if (m > 0) {
            target.setX(x / m);
            target.setY(y / m);
        } else {
            target.setX(m);
            target.setY(m);
        }
        return target;
    }

    public void limit(float max) {
        if (magnitude() > max) {
            normalize();
            mult(max);
        }
    }

    public float angle() {
        double angle = Math.atan2(y, x);
        return (float) angle;
    }

    public float clampRadians(float radians) {
        return (float) (radians % (Math.PI * 2));
    }

    public void rotate(double radians){
        double cr = Math.cos(radians);
        double sr = Math.sin(radians);
        setX((float) (cr*x - sr*y));
        setY((float) (sr*x + cr*y));
    }
    public static VectorXY fromPolar(float radians, float module){
        double x = Math.cos(radians) * module;
        double y = Math.sin(radians) * module;
        return new VectorXY((float)x,(float)y);
    }
    public static VectorXY fromDir(Dir dir, float module){
        float degrees = dir.getValue() * 45;
        float radians = toRadians(degrees);
        return fromPolar(radians, module);
    }
    public Dir toDir(){
        return Dir.fromInt((int) cardinal(angle(), 8));
    }
    public String toString() {
        return "[" +String.format("%.2f", x) + ", "+ String.format("%.2f",y) +"]";
    }

    // STATIC /////////////////////////////////////
    public static float dot(VectorXY v1, VectorXY v2) {
        return v1.x * v2.x + v1.y * v2.y;
    }
    public static VectorXY div(VectorXY v1, VectorXY v2) {
        return div(v1, v2, null);
    }
    public static VectorXY mult(VectorXY v, float n) {
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
    public static VectorXY mult(VectorXY v, float n, VectorXY target) {
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
    public static VectorXY div(VectorXY v, float n) {
        return div(v, n, null);
    }
    public static VectorXY div(VectorXY v, float n, VectorXY target) {
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
    public static float distance(VectorXY v1, VectorXY v2) {
        float dx = v1.x - v2.x;
        float dy = v1.y - v2.y;

        return (float) Math.sqrt(dx *dx + dy * dy);
    }
    public static float toRadians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
    public static float toDegrees(float radians) {
        return (float) Math.toDegrees(radians);
    }
    public static float angleBetween(VectorXY v1, VectorXY v2) {
        float arc = (v1.x * v1.x + v1.y * v1.y) * (v2.x * v2.x + v2.y * v2.y);
        arc = (float) Math.sqrt(arc);
        if (arc > 0) {
            arc = (float) Math.acos((v1.x * v2.x + v1.y * v2.y) / arc);
            if (v1.x * v2.y - v1.y * v2.x < 0) {
                arc = -arc;
            }
        }
        return arc;
    }

    public static float simpleAngleBetween(VectorXY v1, VectorXY v2) {
        double a = Math.atan2(v2.y, v2.x) - Math.atan2(v1.y, v1.x);
        return (float) ((a + Math.PI) % (Math.PI * 2) - Math.PI);
    }

    public static double cardinal(float angle, int steps){
        return Math.floor(steps*angle/(2*Math.PI)+steps +0.5)%steps;
    }
}