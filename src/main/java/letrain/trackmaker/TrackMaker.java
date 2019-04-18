package letrain.trackmaker;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.TerrainMap;
import letrain.mvp.GameModel;
import letrain.track.Track;

public interface TrackMaker<T extends Track> {
    enum NewTrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    void setMap(TerrainMap<T> map);

    void setPosition(int x, int y);

    Point getPosition();

    void advance(int times);

    void advance();

    void setDirection(Dir d);

    void selectNewTrackType(RailTrackMaker.NewTrackType type);

    Dir getDirection();

    void reverse();

    void rotateLeft();

    void rotateRight();

    boolean isReversed();

    void setMode(GameModel.Mode mode);

    GameModel.Mode getMode();

}
