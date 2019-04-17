package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public class StopTrack extends Track{
    public StopTrack() {
        setTrackDirector(new TrackDirector(){
            @Override
            public void enterLinkerFromDir(Track track, Dir d, Linker vehicle) {

            }

            @Override
            public Linker removeLinker(Track track) {
                return null;
            }

            @Override
            public boolean canEnter(Track track, Dir d, Trackeable v) {
                return false;
            }

            @Override
            public boolean canExit(Track track, Dir d) {
                return true;
            }
        });
    }
}
