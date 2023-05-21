package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.vehicle.impl.Linker;

public class Wagon extends Linker {
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
        this.destroyingTurns = 1000;
    }

    @Override
    public boolean isDestroying() {
        return this.destroying;
    }

    @Override
    public boolean isDestroyed() {
        if (isDestroying() && destroyingTurns-- <= 0) {
            return true;
        }
        return false;
    }
}
