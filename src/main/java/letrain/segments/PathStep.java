package letrain.segments;

import letrain.map.Dir;

/**
 * Una intención o decisión en un nodo: (RailNode, Dir).
 */
public interface PathStep {
    RailNode getRailNode();
    Dir getDir();
}
