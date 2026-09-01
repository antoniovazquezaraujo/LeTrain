package letrain.track.rail;

public class StationRailTrack extends RailTrack {
    private letrain.map.Dir creationDir = letrain.map.Dir.N;

    public letrain.map.Dir getCreationDir() {
        return creationDir;
    }

    public void setCreationDir(letrain.map.Dir creationDir) {
        this.creationDir = creationDir;
    }
}
