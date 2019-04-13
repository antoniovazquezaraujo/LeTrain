package letrain.map;

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

    void setMode(Mode mode);

    Mode getMode();

    enum Mode {
        MAP_WALK,
        TRACK_WALK,
        MAKE_TRACK,
        REMOVE_TRACK
    }
}
