package letrain.physics;

import letrain.vehicle.impl.Cursor;

public interface PhysicVisitor {
    void visitModel(PhysicLabSpace model);

    void visitBody(Body2D body);

    void visitCursor(Cursor cursor);
}
