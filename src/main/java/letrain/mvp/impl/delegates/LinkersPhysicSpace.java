package letrain.mvp.impl.delegates;

import javafx.util.Pair;
import letrain.map.Dir;
import letrain.physics.Vector2D;
import letrain.vehicle.impl.Linker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static letrain.vehicle.TangibleBody.ContactResult.BUMP;
import static letrain.vehicle.TangibleBody.ContactResult.CRASH;


public class LinkersPhysicSpace   {
    List<Linker> linkers;

    public LinkersPhysicSpace() {
        linkers = new ArrayList<>();
    }

    public void addLinker(Linker linker) {
        this.linkers.add(linker);
    }

    public void removeLinker(Linker linker) {
        linkers.remove(linker);
    }

    public List<Linker> getLinkers() {
        return linkers;
    }

    public void moveLinkers() {
        calc();
    }

    public Optional<Linker> detectCandidate(Linker linker, Vector2D position) {
        return linkers.stream()
                .filter(t -> {
                    boolean b = t != linker;
                    boolean equals = t.getPosition2D().almostEquals(position);
                    return b && equals;
                })
                .findAny();
    }


    void calc() {
        for (Linker linker : this.linkers) {
            linker.beginStep();
            linker.applyForces();
            if (linker.getDistanceTraveledInStep() > 100) {
                Vector2D futurePosition = new Vector2D(linker.getPosition2D());
                linker.move(futurePosition, linker.getHeading2D());
                Optional<Linker> candidate = detectCandidate(linker, futurePosition);
                if (candidate.isPresent()) {
                    Linker.ContactResult result = applyContactImpulses(new Pair(linker, candidate.get()));
                    onContact(result, linker, candidate.get());
                } else {
                    linker.endStep();
                }
            }
        }
    }

    protected void onContact(Linker.ContactResult contactResult, Linker linker1, Linker linker2) {
        linker1.onContact(contactResult, linker2);
        linker2.onContact(contactResult, linker1);
    }

    /*
        Para calcular las velocidades de los linkers despues del choque, u1 y u2 haremos lo siguiente:
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
    Linker.ContactResult applyContactImpulses(Pair<Linker, Linker> linkers) {
        Linker b1 = linkers.getKey();
        Linker b2 = linkers.getValue();
        Vector2D v1 = new Vector2D(b1.getVelocity2D());
        Vector2D v2 = new Vector2D(b2.getVelocity2D());
        double m1 = b1.getMass();
        double m2 = b2.getMass();
        double totalMass = m1 + m2;
        Vector2D energy1 = Vector2D.mult(v1, m1);
        Vector2D energy2 = Vector2D.mult(v2, m2);


        Vector2D u1 = Vector2D.div(Vector2D.add(Vector2D.mult(v1, (m1 - m2)), (Vector2D.mult(energy2, 2))), (totalMass));
        Vector2D u2 = Vector2D.div(Vector2D.add(Vector2D.mult(v2, (m2 - m1)), (Vector2D.mult(energy1, 2))), (totalMass));
        Dir oldB1Dir = b1.getDir();
        double oldB1Velocity = b1.getVelocity2D().magnitude();
        b1.setVelocity2D(u1);
        Dir newB1Dir = b1.getDir();
        b1.setDistanceTraveledInStep(0);

        Dir oldB2Dir = b2.getDir();
        double oldB2Velocity = b2.getVelocity2D().magnitude();
        b2.setVelocity2D(u2);
        Dir newB2Dir = b2.getDir();
        b2.setDistanceTraveledInStep(0);

        Linker.ContactResult contactResult = BUMP;
        if ((oldB1Dir != newB1Dir) || (oldB2Dir != newB2Dir)) {
            contactResult = Linker.ContactResult.BOUNCE;
        }
        if (
                (Math.abs(oldB1Velocity - b1.getVelocity2D().magnitude()) > 100)
                        ||
                        (Math.abs(oldB2Velocity - b2.getVelocity2D().magnitude()) > 100)
        ) {
            contactResult = CRASH;
        }

        return contactResult;
    }
}
