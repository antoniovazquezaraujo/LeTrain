package letrain.tui;

import letrain.mvp.GameView;
import letrain.map.RailMap;

class RailMapViewer {

    public static void view(RailMap map) {
        final GameView tui = new Tui();
        GraphicConverter converter = new BasicGraphicConverter();
        map.forEach(t -> tui.set(
                t.getPosition().getX(),
                t.getPosition().getY(),
                converter.getTrackAspect(t)));
        tui.paint();
    }
}
