package letrain.mvp;

public interface Presenter extends GameViewListener {


    enum TrackType {
        NORMAL_TRACK,
        STATION_TRACK,
        TUNNEL_TRACK,
        TUNNEL_GATE_TRACK,
        BRIDGE_TRACK,
        BRIDGE_GATE_TRACK
    }

    View getView();

    Model getModel();
}
