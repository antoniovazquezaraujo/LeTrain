package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker implements Tractor {
    static int numLocomotivesCreated = 0;
    final static int MAX_SPEED = 5;
    int speed;
    int turns;
    private String aspect;
    int showingDirTurns;
    int id;

    public Locomotive(String aspect) {
        this.id = (++numLocomotivesCreated);
        this.aspect = aspect;
    }

    public Locomotive(char c) {
        this("" + c);
    }

    public int getId() {
        return this.id;
    }

    @Override
    public void toggleReversed() {
        Dir pushDir = getDir();
        Track nextTrack = getTrack();
        setDir(nextTrack.getDir(pushDir));
        setReversed(!isReversed());
        showingDirTurns = 5;
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        // for (Linker linker : this.getTrain().getLinkers()) {
        // if (linker instanceof Locomotive) {
        visitor.visitLocomotive(this);
        // } else {
        // visitor.visitWagon((Wagon) linker);
        // }
        // }
    }

    public String getAspect() {
        return aspect;
    }

    public void update() {
        if (isDirectorLinker()) {
            if (isTimeToMove()) {
                getTrain().advance();
                resetTurns();
            } else {
                consumeTurn();
            }
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

    public boolean isDirectorLinker() {
        return getTrain() != null && getTrain().getDirectorLinker() == this;
    }

    public boolean isShowingDir() {
        if (showingDirTurns > 0) {
            showingDirTurns--;
            return true;
        }
        return false;
    }
}
