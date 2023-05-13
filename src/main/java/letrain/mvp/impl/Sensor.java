package letrain.mvp.impl;

import letrain.track.Track;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Sensor implements Renderable {
    private static int numSensorsCreated = 0;
    private int id;
    Track track;

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public Sensor() {
        setId(++numSensorsCreated);
    }

    private void setId(int i) {
        this.id = i;
    }

    public int getId() {
        return this.id;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSensor(this);
    }

}
