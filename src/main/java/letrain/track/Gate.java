package letrain.track;

import letrain.map.Router;
import letrain.visitor.Visitor;

public class Gate extends Track {
    @Override
    public void accept(Visitor visitor) {

    }

    @Override
    public Router getRouter() {
        return null;
    }
}
