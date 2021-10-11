package letrain.vehicle;

public interface Braker {
    void incBrakes(int i);

    void decBrakes(int i);

    double getBrakes();

    void setBrakes(double i);

    void activateBrakes(boolean active);

    boolean isBrakesActivated();
}
