package letrain.track;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import letrain.map.Point;
import letrain.map.Dir;
import letrain.vehicle.rail.impl.Train;
import letrain.visitor.Visitor;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class SpeedSignal extends Sensor implements Serializable {
    private static final long serialVersionUID = 1L;

    private int limit;
    private boolean isMax;

    public SpeedSignal() {
        super();
    }

    public SpeedSignal(int id, Dir creationDir, int limit, boolean isMax) {
        super(id);
        setCreationDir(creationDir);
        this.limit = limit;
        this.isMax = isMax;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isMax() {
        return isMax;
    }

    public void setMax(boolean isMax) {
        this.isMax = isMax;
    }

    @Override
    public void onSensorEnter(Train train, boolean isForward) {
        super.onSensorEnter(train, isForward);
        
        if (!isForward) {
            return; // Speed signal only acts in the direction it faces
        }

        int progSpeed = train.getProgrammedSpeed();
        
        if (isMax) {
            // RED SIGNAL: Max Limit
            if (progSpeed > limit) {
                train.applySpeedRestriction(limit);
            } else {
                train.applySpeedRestriction(progSpeed);
            }
        } else {
            // BLUE SIGNAL: Min Limit
            if (progSpeed < limit) {
                train.applySpeedRestriction(limit);
            } else {
                train.applySpeedRestriction(progSpeed);
            }
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSpeedSignal(this);
    }
}
