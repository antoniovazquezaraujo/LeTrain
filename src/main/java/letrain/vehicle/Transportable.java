package letrain.vehicle;

public interface Transportable {
    boolean advance();
    float getMass();
    float getFrictionCoefficient();
    float getAcceleration();
    float getSpeed();
    float getDistanceTraveled();
    void resetDistanceTraveled();
}
