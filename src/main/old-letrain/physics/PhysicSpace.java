package letrain.physics;

import javafx.util.Pair;
import letrain.map.Dir;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static letrain.physics.Body2D.ContactResult.BUMP;
import static letrain.physics.Body2D.ContactResult.CRASH;

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
        calc();
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
        for (Body2D body : this.bodies) {
            body.beginStep();
            body.applyForces();
            if (body.distanceTraveledInStep > 100) {
                Vector2D futurePosition = new Vector2D(body.position);
                Body2D.move(futurePosition, body.heading);
                Optional<Body2D> candidate = detectCandidate(body, futurePosition);
                if (candidate.isPresent()) {
                    Body2D.ContactResult result = applyContactImpulses(new Pair(body, candidate.get()));
                    onContact(result, body, candidate.get());
                } else {
                    body.endStep();
                }
            }
        }
    }

    protected void onContact(Body2D.ContactResult contactResult, Body2D body1, Body2D body2) {
        body1.onContact(contactResult, body2);
        body2.onContact(contactResult, body1);
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
    Body2D.ContactResult applyContactImpulses(Pair<Body2D, Body2D> bodies) {
        Body2D b1 = bodies.getKey();
        Body2D b2 = bodies.getValue();
        Vector2D v1 = new Vector2D(b1.velocity);
        Vector2D v2 = new Vector2D(b2.velocity);
        double m1 = b1.mass;
        double m2 = b2.mass;
        double totalMass = m1 + m2;
        Vector2D energy1 = Vector2D.mult(v1, m1);
        Vector2D energy2 = Vector2D.mult(v2, m2);


        Vector2D u1 = Vector2D.div(Vector2D.add(Vector2D.mult(v1, (m1 - m2)), (Vector2D.mult(energy2, 2))), (totalMass));
        Vector2D u2 = Vector2D.div(Vector2D.add(Vector2D.mult(v2, (m2 - m1)), (Vector2D.mult(energy1, 2))), (totalMass));
        Dir oldB1Dir = b1.getDir();
        double oldB1Velocity = b1.getVelocity().magnitude();
        b1.setVelocity(u1);
        Dir newB1Dir = b1.getDir();
        b1.distanceTraveledInStep = 0;

        Dir oldB2Dir = b2.getDir();
        double oldB2Velocity = b2.getVelocity().magnitude();
        b2.setVelocity(u2);
        Dir newB2Dir = b2.getDir();
        b2.distanceTraveledInStep = 0;

        Body2D.ContactResult contactResult = BUMP;
        if ((oldB1Dir != newB1Dir) || (oldB2Dir != newB2Dir)) {
            contactResult = Body2D.ContactResult.BOUNCE;
        }
        if (
                (Math.abs(oldB1Velocity - b1.getVelocity().magnitude()) > 100)
                        ||
                        (Math.abs(oldB2Velocity - b2.getVelocity().magnitude()) > 100)
        ) {
            contactResult = CRASH;
        }

        return contactResult;
    }
}
