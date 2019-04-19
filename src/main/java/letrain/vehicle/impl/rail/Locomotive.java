package letrain.vehicle.impl.rail;

import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.render.Renderer;

public class Locomotive extends Linker implements Tractor{

    private float force;

    @Override
    public float getMass() {
        return super.getMass()+5000;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.2F;
    }

    @Override
    public float getForce() {
        return this.force;
    }

    @Override
    public void setForce(float force) {
        this.force = force;
    }
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderLocomotive(this);
    }

}
