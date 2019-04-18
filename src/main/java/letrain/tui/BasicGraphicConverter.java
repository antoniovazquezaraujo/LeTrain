package letrain.tui;

import letrain.map.Dir;
import letrain.trackmaker.RailTrackMaker;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;

public class BasicGraphicConverter implements GraphicConverter {

    @Override
    public String getTrackAspect(Track track) {
        if (track.getRouter().isStraight()) {
            return dirGraphicAspect(track.getRouter().getFirstOpenDir());
        } else if (track.getRouter().isCurve()) {
            return ".";
        }else if (track.getRouter().isFork()){
            return "Y";
//            if(track.getRouter().isUsingAlternativeRoute()){
//                return dirGraphicAspect(track.getRouter().getAlternativeRoute().getTarget());
//            }else{
//                return dirGraphicAspect(track.getRouter().getOriginalRoute().getTarget());
//            }
        } else {
            return "+";
        }
    }
    private String dirGraphicAspect(Dir dir){
        if(dir==null){
            return "?";
        }
        switch(dir) {
            case E:
            case W:
                return "-";
            case NE:
            case SW:
                return "/";
            case N:
            case S:
                return "|";
            case NW:
            case SE:
                return "\\";
        }
        return "?";
    }

    @Override
    public String getLinkerAspect(Linker linker) {
        return "O";
    }

    @Override
    public String getRailTrackMakerAspect(RailTrackMaker maker) {
        return "@";
    }
}
