package letrain.utils;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Vector3;
import letrain.map.Point;

/**
 * Utilidad para calcular trayectorias suaves (Splines) a partir de la
 * cuadrícula de vías.
 */
public class SplinePath {
    private final CatmullRomSpline<Vector3> spline;
    private final Vector3[] controlPoints;

    public SplinePath(List<Point> points) {
        if (points.size() < 4) {
            // Catmull-Rom requiere al menos 4 puntos para definir un segmento (p0, p1, p2,
            // p3)
            // donde la curva va de p1 a p2.
            this.controlPoints = computeDefaultControlPoints(points);
        } else {
            this.controlPoints = points.stream()
                    .map(p -> new Vector3(p.getX(), 0, p.getY()))
                    .toArray(Vector3[]::new);
        }
        this.spline = new CatmullRomSpline<>(controlPoints, true);
    }

    private Vector3[] computeDefaultControlPoints(List<Point> points) {
        // Lógica simple para rellenar puntos si hay pocos (duplicar extremos)
        List<Vector3> vPoints = new ArrayList<>();
        if (!points.isEmpty()) {
            Point first = points.get(0);
            Point last = points.get(points.size() - 1);
            vPoints.add(new Vector3(first.getX(), 0, first.getY())); // p0
            for (Point p : points) {
                vPoints.add(new Vector3(p.getX(), 0, p.getY()));
            }
            vPoints.add(new Vector3(last.getX(), 0, last.getY())); // pn+1
        }
        return vPoints.toArray(new Vector3[0]);
    }

    public Vector3 getPositionAt(float t) {
        Vector3 out = new Vector3();
        spline.valueAt(out, t);
        return out;
    }

    public Vector3 getTangentAt(float t) {
        Vector3 out = new Vector3();
        spline.derivativeAt(out, t);
        return out.nor();
    }
}
