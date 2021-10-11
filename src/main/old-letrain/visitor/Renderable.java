package letrain.visitor;

public interface Renderable {
    void accept(Visitor visitor);
}
