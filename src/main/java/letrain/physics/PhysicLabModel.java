package letrain.physics;

import letrain.vehicle.impl.Cursor;

public interface PhysicLabModel {
    Cursor getCursor();
    Body2D getSelectedBody();
    void setSelectedBody(Body2D body);
}
