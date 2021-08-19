package letrain.physics;

import letrain.physics.Body2D;
import letrain.vehicle.impl.Cursor;

import java.util.List;

public interface PhysicModel {
    List<Body2D> getBodies();

    Cursor getCursor();

    void addBody(Body2D body);

    void removeBody(Body2D body);

    void moveBodies();

    Mode getMode();

    void setMode(Mode mode);

    Body2D getSelectedBody();


    void setSelectedBody(Body2D body);

    enum Mode {
        BODIES("Use bodies"),
        MAKE_BODIES("Create bodies");

        private String name;

        Mode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
