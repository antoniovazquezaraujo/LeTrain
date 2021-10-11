package letrain.vehicle;


import letrain.map.Positionable;
import letrain.map.Positionable2D;
import letrain.map.Reversible;

import letrain.visitor.Renderable;

import java.io.Serializable;

public interface TangibleBody extends Orientable, Positionable2D, Reversible, Serializable, Renderable {
      enum ContactResult {BUMP, BOUNCE, CRASH}

    void onContact( ContactResult contactResult, TangibleBody vehicle);

}
