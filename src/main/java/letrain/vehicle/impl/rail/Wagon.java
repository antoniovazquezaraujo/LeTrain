package letrain.vehicle.impl.rail;

import letrain.vehicle.impl.Linker;
import letrain.view.Renderer;

public class Wagon extends Linker {

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

}
