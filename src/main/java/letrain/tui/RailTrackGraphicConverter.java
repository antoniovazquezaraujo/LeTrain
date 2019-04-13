package letrain.tui;

import letrain.map.Dir;
import letrain.track.rail.RailTrack;

public class RailTrackGraphicConverter implements GraphicConverter<RailTrack> {
    @Override
    public char getAspect(RailTrack track) {
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return '.';
        }else if (track.getRouter().isFork()){
            return 'Y';
//            if(track.getRouter().isUsingAlternativeRoute()){
//                return dirGraphicAspect(track.getRouter().getAlternativeRoute().getTarget());
//            }else{
//                return dirGraphicAspect(track.getRouter().getOriginalRoute().getTarget());
//            }
        } else {
            return '+';
        }
    }
    public char dirGraphicAspect(Dir dir){
        if(dir==null){
            return '?';
        }
        switch(dir) {
            case E:
            case W:
                return '-';
            case NE:
            case SW:
                return '/';
            case N:
            case S:
                return '|';
            case NW:
            case SE:
                return '\\';
        }
        return '?';
    }
}
