package letrain.vehicle;

import letrain.track.Track;

public interface Transportable {
    Track move();

    float getMass();

    float getFrictionCoefficient();

    float getAcceleration();

    void setAcceleration(float speed);

    float getDistanceTraveled();

    void resetDistanceTraveled();

    void incBrakes(int i);

    void decBrakes(int i);

    float getBrakes();

    void setBrakes(float i);
}
