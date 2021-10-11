package letrain.vehicle;

import letrain.physics.Vector2D;

public interface MovableBody extends TangibleBody {
    Vector2D getVelocity2D();
    void setVelocity2D(Vector2D velocity);
    double getVelocity();
}
