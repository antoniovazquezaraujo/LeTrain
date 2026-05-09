package letrain.vehicle.impl.rail;

import letrain.track.CargoTypes;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Visitor;

public class Wagon extends Linker {
    public static final int MAX_CARGO_CAPACITY = 50;
    private static final int MAX_DESTROY_TURNS = 200;
    String aspect;
    float brakes;
    boolean destroying = false;
    int destroyingTurns = 0;

    public Wagon(String aspect) {
        this.aspect = aspect;
    }

    public Wagon() {
        this("?");
    }

    public Wagon(char c) {
        this("" + c);
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitWagon(this);
    }

    public String getAspect() {
        return aspect;
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }

    @Override
    public void destroy() {
        this.destroying = true;
        this.destroyingTurns = MAX_DESTROY_TURNS;
    }

    @Override
    public boolean isDestroying() {
        return this.destroying;
    }

    @Override
    public boolean isDestroyed() {
        return isDestroying() && destroyingTurns <= 0;
    }

    public void updateDestroyTimer() {
        if (isDestroying() && destroyingTurns > 0) {
            destroyingTurns--;
        }
    }

    private int cargoAmount = 0;
    private int maxCapacity = MAX_CARGO_CAPACITY;
    private CargoTypes cargoType = CargoTypes.NONE;
    private CargoTypes exclusiveCargoType = CargoTypes.NONE;
    private letrain.map.Point loadingPoint = null;

    public CargoTypes getExclusiveCargoType() {
        return exclusiveCargoType;
    }

    public void setExclusiveCargoType(CargoTypes exclusiveCargoType) {
        this.exclusiveCargoType = exclusiveCargoType;
    }

    public CargoTypes getCargoType() {
        return cargoType;
    }

    public void setCargoType(CargoTypes cargoType) {
        this.cargoType = cargoType;
    }

    public letrain.map.Point getLoadingPoint() {
        return loadingPoint;
    }

    public void setLoadingPoint(letrain.map.Point loadingPoint) {
        this.loadingPoint = loadingPoint;
    }

    public int getCargoAmount() {
        return cargoAmount;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void load(int amount) {
        this.cargoAmount += amount;
        if (this.cargoAmount > this.maxCapacity) {
            this.cargoAmount = this.maxCapacity;
        }
    }

    public void unload(int amount) {
        this.cargoAmount -= amount;
        if (this.cargoAmount < 0) {
            this.cargoAmount = 0;
        }
    }

    public boolean isFull() {
        return cargoAmount >= maxCapacity;
    }
}
