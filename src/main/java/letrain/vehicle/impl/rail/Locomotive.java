package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker implements Tractor {
    final static int MAX_SPEED = 5;
    int speed;
    int turns;
    private String aspect;

    public Locomotive(String aspect) {
        this.aspect = aspect;
    }

    public Locomotive(char c) {
        this("" + c);
    }

    @Override
    public void toggleReversed() {
        Dir pushDir = getDir();
        Track nextTrack = getTrack();
        setDir(nextTrack.getDir(pushDir));
        setReversed(!isReversed());
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        for (Linker linker : this.getTrain().getLinkers()) {
            if (linker instanceof Locomotive) {
                visitor.visitLocomotive((Locomotive) linker);
            } else {
                visitor.visitWagon((Wagon) linker);
            }
        }
    }

    public String getAspect() {
        return aspect;
    }

    public void update() {
        if (isTimeToMove()) {
            getTrain().advance();
            resetTurns();
        } else {
            consumeTurn();
        }
    }

    public void incSpeed() {
        this.speed++;
        limitSpeed();
        resetTurnsIfNeeded();
    }

    public void decSpeed() {
        this.speed--;
        limitSpeed();
        resetTurnsIfNeeded();
    }

    public void setSpeed(int speed) {
        this.speed = speed;
        limitSpeed();
        resetTurnsIfNeeded();
    }

    private void limitSpeed() {
        if (this.speed > MAX_SPEED) {
            this.speed = MAX_SPEED;
        }
        if (this.speed < 0) {
            this.speed = 0;
        }
    }

    public int getSpeed() {
        return this.speed;
    }

    public boolean isTimeToMove() {
        if (this.turns == 0) {
            resetTurns();
            return true;
        }
        return false;
    }

    public void resetTurnsIfNeeded() {
        if (turns == -1) {
            resetTurns();
        }
    }

    public void resetTurns() {
        this.turns = speed == 0 ? -1 : 10 / speed;
    }

    public void consumeTurn() {
        if (turns > 0) {
            turns--;
        }
    }
}
