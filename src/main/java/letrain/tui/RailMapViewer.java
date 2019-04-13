package letrain.tui;

import letrain.map.RailMap;
import letrain.track.rail.RailTrack;

public class RailMapViewer {

    public static void view(RailMap map) {
        final SimpleUI tui = new Tui();
        GraphicConverter<RailTrack> converter = new RailTrackGraphicConverter();
        map.forEach(t ->{
            tui.set(
                    t.getPosition().getX(),
                    t.getPosition().getY(),
                    converter.getAspect(t));
        });
        tui.paint();
    }
}
