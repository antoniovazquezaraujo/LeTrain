package letrain.vehicle;

import letrain.visitor.Visitor;

public interface Renderable {
    void accept(Visitor visitor);
}
