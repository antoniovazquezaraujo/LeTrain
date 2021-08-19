package letrain.physics;

public interface PhysicRenderable {
    void accept(PhysicVisitor visitor);
}
