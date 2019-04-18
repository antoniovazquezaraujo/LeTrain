package letrain.vehicle.impl.rail;

import letrain.vehicle.impl.Linker;
import letrain.view.Renderer;

public class Wagon extends Linker {
    String aspect;

    public Wagon(String aspect) {
        this.aspect = aspect;
    }
    public Wagon() {
        this("?");
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.3F;
    }
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderWagon(this);
    }
    public String getAspect() {
        return aspect;
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }
}
