package letrain.physics;

import javafx.util.Pair;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.vehicle.impl.Cursor;

import java.util.*;

public class BasicPhysicModel implements PhysicModel {
    List<Body2D> bodies;
    Body2D selectedBody;
    Cursor cursor;
    PhysicModel.Mode mode;

    public BasicPhysicModel() {
        bodies = new ArrayList<>();
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(5, 5));
        this.mode = Mode.BODIES;
    }

    public void addBody(Body2D body) {
        this.bodies.add(body);
    }

    @Override
    public void removeBody(Body2D body) {
        bodies.remove(body);
    }

    @Override
    public PhysicModel.Mode getMode() {
        return this.mode;
    }

    @Override
    public void setMode(PhysicModel.Mode mode) {
        this.mode = mode;
    }

    @Override
    public Body2D getSelectedBody() {
        return selectedBody;
    }

    @Override
    public void setSelectedBody(Body2D body) {
        this.selectedBody = body;
    }

    public List<Body2D> getBodies() {
        return bodies;
    }

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    @Override
    public void moveBodies() {
        calc(bodies);
    }

    boolean isLocked(Vector2D position) {
        return bodies.stream()
                .anyMatch(t -> t.position.equals(position));
    }

    public Optional<Body2D> detectCandidate(Body2D body, Vector2D position) {
        return bodies.stream()
                .filter(t -> {
                    boolean b = t != body;
                    boolean equals = t.position.almostEquals(position);
                    return b && equals;
                })
                .findAny();
    }

    public Crash createCrash(Body2D body) {
        return null;
    }

    void calc() {
        calc(this.bodies);
    }

    void calc(List<Body2D> bodies) {
        Map<Vector2D, Pair<Body2D, Body2D>> crashes = new HashMap<>();
        for (Body2D body : bodies) {
            body.beginStep();
            body.applyForces();
            if (body.distanceTraveledInStep > 100) {
                Dir dir = body.velocity.toDir();
                Vector2D gridVelocity = Vector2D.fromDir(dir, 1);
                Vector2D futurePosition = body.position.add(gridVelocity);
                futurePosition = new Vector2D(Math.round(futurePosition.x), Math.round(futurePosition.y));
                Optional<Body2D> candidate = detectCandidate(body, futurePosition);
                if (candidate.isPresent()) {
                    //crashes.put(futurePosition, new Pair(body, candidate.get()));
                    applyCrashImpulses(new Pair(body, candidate.get()));
                } else {
                    body.endStep();
                }
            }
        }
//        if (!crashes.isEmpty()) {
//            for (Vector2D pos : crashes.keySet()) {
//                Pair<Body2D, Body2D> candidates = crashes.get(pos);
//                applyCrashImpulses(candidates);
//                List param = new ArrayList<Body2D>();
//                param.add(candidates.getKey());
//                param.add(candidates.getValue());
//                calc(param);
//            }
//        }
    }

    /*
    Para calcular las velocidades de los bodies despues del choque, u1 y u2 haremos lo siguiente:
    m = masa
    v = velocidad antes del choque
    u = velocidad después del choque

    El momento permanece igual antes y después, luego:
    m1*v1 + m2 *v2 = m1*u1 + m2 * u2

    La energía cinética era la mitad de la masa por el cuadrado de la velocidad:
        0.5.m*v*v
    Como eso tampoco cambia tenemos:
        0.5 * m1 * v1 * v1 + 0.5 * m2 * v2 * v2 = 0.5 *m1 *u1 * u1 + 0.5 * m2 * u2 * u2
    Resolviendo u1 y u2:
        u1 = ( v1 * ( m1 - m2 ) + 2 * m2 * v2 ) /( m1 + m2 )
        u2 = ( v2 * ( m2 - m1 ) + 2 * m1 * v1 ) /( m1 + m2 )
     */
    void applyCrashImpulses(Pair<Body2D, Body2D> bodies) {
        Body2D b1 = bodies.getKey();
        Body2D b2 = bodies.getValue();
        Vector2D v1 = new Vector2D(b1.velocity);
        Vector2D v2 = new Vector2D(b2.velocity);
        double m1 = b1.mass;
        double m2 = b2.mass;
        v1.mult(m1 - m2);
        v2.mult(2 * m2);
        v1.add(v2);
        v1.div(m1 + m2);

        Vector2D u1 = v1.mult(m1 - m2).add(v2.mult(m2).mult(2)).div(m1 + m2);
        Vector2D u2 = v2.mult(m2 - m1).add(v1.mult(m1).mult(2)).div(m1 + m2);
        b1.addExternalForce(u1);
        b2.addExternalForce(u2);
    }
}