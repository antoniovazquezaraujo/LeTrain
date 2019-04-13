package letrain.map;

/**
 * letrain.map.Dir.
 * <p>
 * Representa una dirección entre las 8 del mapa (N, NE, E...)
 * Se puede hacer girar, invertir, ver si forma recta con otra,
 * calcular la "distancia angular" entre dos, etc.
 */

//  Giran en sentido glorieta, desde E hasta SE
//
//
//           2-N
//     NW-3   |   1-NE
//         \     /
//   W-4 -    .    - 0-E
//         /     \
//     SW-5   |   7-SE
//           6-S
//
//


public enum Dir {
    E(0),
    NE(1),
    N(2),
    NW(3),
    W(4),
    SW(5),
    S(6),
    SE(7);

    public static final int NUM_DIRS = 8;
    public static final int MIDDLE_ANGLE = NUM_DIRS / 2;
    public static final int MIN_CURVE_ANGLE = MIDDLE_ANGLE - 1;
    public static final int MAX_CURVE_ANGLE = MIDDLE_ANGLE + 1;

    private final int value;


    Dir(int value) {
        this.value = value;
    }

    public static Dir random() {
        return fromInt((int) (Math.random() * NUM_DIRS));
    }

    public static Dir fromInt(int n) {
        if (n < 0) {
            n = NUM_DIRS + n;
        } else if (n > SE.getValue()) {
            n = n - NUM_DIRS;
        }
        n %= NUM_DIRS;
        switch (n) {
            case 0:
                return E;
            case 1:
                return NE;
            case 2:
                return N;
            case 3:
                return NW;
            case 4:
                return W;
            case 5:
                return SW;
            case 6:
                return S;
            case 7:
                return SE;
        }
        return null;
    }

    public static Dir add(Dir a, int n) {
        return fromInt(a.value + n);
    }

    static boolean isValidValue(int value) {
        return (value >= E.getValue() && value <= SE.getValue());
    }

    public static Dir invert(Dir dir) {
        return Dir.fromInt(dir.getValue() + MIDDLE_ANGLE);
    }

    public static int invert(int value) {
        if (isValidValue(value)) {
            return value + MIDDLE_ANGLE;
        } else {
            throw new RuntimeException("Invalid int value for direction");
        }

    }

    public static int shortWay(int angle) {
        int absValue = Math.abs(angle);
        if (absValue > MIDDLE_ANGLE) {
            int diff = NUM_DIRS - absValue;
            if (angle > 0) {
                return diff * -1;
            } else {
                return diff;
            }
        } else {
            return angle;
        }
    }

    public int getValue() {
        return value;
    }

    public int angularDistance(Dir d) {
        return shortWay(d.value - this.value);
    }

    public Dir inverse() {
        return fromInt(invert(value));
    }

    public Dir turnLeft() {
        return fromInt(value + 1);
    }

    public Dir turnRight() {
        return fromInt(value - 1);
    }

    public boolean isCurve(Dir to) {
        int distance = Math.abs(this.value - to.getValue());
        return ((distance == MIN_CURVE_ANGLE) || (distance == MAX_CURVE_ANGLE));
    }

    public boolean isStraight(Dir to) {
        return (Math.abs(this.value - to.getValue()) == MIDDLE_ANGLE);
    }

}
