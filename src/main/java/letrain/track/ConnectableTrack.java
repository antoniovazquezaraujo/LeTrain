package letrain.track;

import letrain.map.Dir;


interface ConnectableTrack {
    Track getConnectedTrack();
    Track getConnectedTrack(Dir dir);

    boolean connectTrack(Dir dir, Track t);

    Track disconnectTrack(Dir dir);
}
