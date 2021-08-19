package letrain.physics;

import letrain.physics.PhysicModel;
import letrain.physics.Body2D;
import letrain.vehicle.impl.Cursor;

public interface PhysicVisitor {
    void visitModel(PhysicModel model);
    void visitBody(Body2D body);
    void visitCursor(Cursor cursor);
}
