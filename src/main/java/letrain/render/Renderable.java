package letrain.render;

public interface Renderable {
    void accept(Visitor visitor);
}
