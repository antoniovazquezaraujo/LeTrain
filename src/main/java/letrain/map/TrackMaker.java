package letrain.map;

import letrain.sim.GameModel;
import letrain.track.Track;

public interface TrackMaker<T extends Track> {
    void setMap(TerrainMap<T> map);

    void setPosition(int x, int y);

    Point getPosition();

    void advance(int times);

    void advance();

    void setDirection(Dir d);

    Dir getDirection();

    void reverse();
    void rotateLeft();
    void rotateRight();

    boolean isReversed();

    void setMode(GameModel.Mode mode);

    GameModel.Mode getMode();

}
