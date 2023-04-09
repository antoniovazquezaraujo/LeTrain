package letrain.map;

public interface Reversible {
    void setReversed(boolean reversed);

    void toggleReversed();

    boolean isReversed();
}
