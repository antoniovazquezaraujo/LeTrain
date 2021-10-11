package letrain.physics;

import java.util.List;

public interface PhysicModel {
    List<Body2D> getLinkers();

    void addBody(Body2D body);

    void removeBody(Body2D body);

    void moveBodies();
}
