package letrain.physics;

import javafx.util.Pair;

import java.util.*;

public abstract class PhysicSpace implements PhysicModel {
    List<Body2D> bodies;

    public PhysicSpace() {
        bodies = new ArrayList<>();
    }

    public void addBody(Body2D body) {
        this.bodies.add(body);
    }

    @Override
    public void removeBody(Body2D body) {
        bodies.remove(body);
    }

    public List<Body2D> getBodies() {
        return bodies;
    }

    @Override
    public void moveBodies() {
        calc(bodies);
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



    void calc() {
        calc(this.bodies);
    }

    void calc(List<Body2D> bodies) {
        Map<Vector2D, Pair<Body2D, Body2D>> crashes = new HashMap<>();
        for (Body2D body : bodies) {
            body.beginStep();
            body.applyForces();
            if (body.distanceTraveledInStep > 100) {
                Vector2D futurePosition = new Vector2D(body.position);
                Body2D.move(futurePosition, body.heading);
                Optional<Body2D> candidate = detectCandidate(body, futurePosition);
                if (candidate.isPresent()) {
                    applyCrashImpulses(new Pair(body, candidate.get()));
                } else {
                    body.endStep();
                }
            }
        }
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
           (0.5 * m1 * v1 * v1) + (0.5 * m2 * v2 * v2) = (0.5 *m1 *u1 * u1) + (0.5 * m2 * u2 * u2)
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
        double totalMass = m1+m2;
        Vector2D energy1 = Vector2D.mult(v1, m1);
        Vector2D energy2 = Vector2D.mult(v2, m2);
        Vector2D momentum = Vector2D.add(energy1, energy2);
//        System.out.println("Momentum: "+ momentum);

        Vector2D u1 = Vector2D.div(Vector2D.add(Vector2D.mult(v1, (m1 - m2)), (Vector2D.mult(energy2, 2))), (totalMass));
        Vector2D u2 = Vector2D.div(Vector2D.add(Vector2D.mult(v2, (m2 - m1)), (Vector2D.mult(energy1, 2))), (totalMass));
        Vector2D diff1 = Vector2D.sub(u1, v1);
        Vector2D diff2 = Vector2D.sub(u2, v2);
        System.out.println("diff: "+ diff1 + ", " + diff2);
        b1.setVelocity(u1);
        b1.distanceTraveledInStep = 0;
        b2.setVelocity(u2);
        b2.distanceTraveledInStep = 0;
    }
}
