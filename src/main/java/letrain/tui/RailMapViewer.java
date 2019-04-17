package letrain.tui;

import letrain.map.RailMap;

public class RailMapViewer {

    public static void view(RailMap map) {
        final SimpleUI tui = new Tui();
        GraphicConverter converter = new BasicGraphicConverter();
        map.forEach(t ->{
            tui.set(
                    t.getPosition().getX(),
                    t.getPosition().getY(),
                    converter.getTrackAspect(t));
        });
        tui.paint();
    }
}
