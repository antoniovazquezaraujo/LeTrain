package letrain.vehicle;

import letrain.vehicle.MovableBody;

public interface Vehicle extends MovableBody {
    void move();
    void beginStep();
    void endStep();
    double getDistanceTraveledInStep();
    void setDistanceTraveledInStep(double distance);

}
