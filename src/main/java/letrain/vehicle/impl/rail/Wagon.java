package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.vehicle.impl.Linker;

public class Wagon extends Linker {
    String aspect;
    float brakes;

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
}
