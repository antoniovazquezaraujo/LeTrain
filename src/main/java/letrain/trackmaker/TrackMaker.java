package letrain.trackmaker;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.TerrainMap;
import letrain.mvp.GameModel;
import letrain.track.Track;
import letrain.render.Renderable;

public interface TrackMaker<T extends Track> extends Renderable {
    enum NewTrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    void setMap(TerrainMap<T> map);

    void setPosition(int x, int y);

    Point getPosition();

    void setDirection(Dir d);

    Dir getDirection();

    void advance();

    void advance(int times);

    void reverse();

    void rotateLeft();

    void rotateRight();

    boolean isReversed();

    void setMode(GameModel.Mode mode);

    void selectNewTrackType(RailTrackMaker.NewTrackType type);

    RailTrackMaker.NewTrackType getNewTrackType();

    GameModel.Mode getMode();
}
