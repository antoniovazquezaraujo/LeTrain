package letrain.physics;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.vehicle.impl.Cursor;

public class PhysicLabSpace extends PhysicSpace implements PhysicLabModel {
    Body2D selectedBody;
    Cursor cursor;

    public PhysicLabSpace() {
        super();
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition2D(new Vector2D(5, 5));
    }

    @Override
    public Body2D getSelectedBody() {
        return selectedBody;
    }

    @Override
    public void setSelectedBody(Body2D body) {
        this.selectedBody = body;
    }

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    boolean isLocked(Vector2D position) {
        return bodies.stream()
                .anyMatch(t -> t.position.equals(position));
    }

}