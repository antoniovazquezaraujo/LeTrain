package letrain.physics;

import javafx.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

class Physic {
    List<Body> bodies;

    public Physic() {
        bodies = new ArrayList<>();
    }

    public void move() {
        for (Body body : bodies) {
            body.beginStep();
            body.applyForces();
            body.endStep();
        }
    }

    boolean isLocked(VectorXY position) {
        return bodies.stream()
                .anyMatch(t -> t.position.equals(position) || t.positionReachedInStep.equals(position));
    }

    public Optional<Body> detectCandidate(VectorXY position) {
        return bodies.stream()
                .filter(t -> t.position.equals(position) || t.positionReachedInStep.equals(position))
                .findAny();
    }

    public Crash createCrash(Body body) {
        return null;
    }

    void calc(List<Body> bodies) {
        Map<VectorXY, Pair<Body, Body>> crashes = new HashMap<>();
        for (Body body : bodies) {
            body.beginStep();
            body.applyForces();
            if (body.distanceTraveledInStep > 0) {
                VectorXY pos = body.positionReachedInStep;
                Optional<Body> candidate = detectCandidate(pos);
                if (!candidate.isPresent()) {
                    crashes.put(pos, new Pair(body, candidate.get()));
                } else {
                    body.endStep();
                    body.move();
                }
            }
        }
        if (!crashes.isEmpty()) {
            for (VectorXY pos : crashes.keySet()) {
                Pair<Body, Body> candidates = crashes.get(pos);
                VectorXY momentum = sumEnergies(candidates);
                List param = new ArrayList<Body>();
                param.add(candidates.getKey());
                param.add(candidates.getValue());
                calc(param);
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
        0.5 * m1 * v1 * v1 + 0.5 * m2 * v2 * v2 = 0.5 *m1 *u1 * u1 + 0.5 * m2 * u2 * u2
    Resolviendo u1 y u2:
        u1 = ( v1 * ( m1 - m2 ) + 2 * m2 * v2 ) /( m1 + m2 )
        u2 = ( v2 * ( m2 - m1 ) + 2 * m1 * v1 ) /( m1 + m2 )
     */
    VectorXY sumEnergies(Pair<Body, Body> bodies) {
        Body b1 = bodies.getKey();
        Body b2 = bodies.getValue();
        VectorXY v1 = new VectorXY(b1.velocity);
        VectorXY v2 = new VectorXY(b2.velocity);
        double m1 = b1.mass;
        double m2 = b2.mass;
        v1.mult(m1-m2);
        v2.mult(2*m2);
        v1.add(v2);
        v1.div(m1+m2);
        return v1;
    }
}