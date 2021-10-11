package letrain.vehicle;

import letrain.map.Dir;
import letrain.physics.Vector2D;

public interface Orientable {
    Vector2D getHeading2D();
    void setHeading2D(Vector2D heading);
    Dir getDir();
    void setDir(Dir dir);

}
