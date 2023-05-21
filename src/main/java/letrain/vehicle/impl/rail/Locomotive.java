package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker implements Tractor {
    private static final long serialVersionUID = 1L;
    final static int MAX_SPEED = 10;
    final static int SPEED_CHANGE_MAX_RELUCTANCE = 2;
    int speedChangeReluctance = SPEED_CHANGE_MAX_RELUCTANCE;
    int speed;
    int turns;
    private String aspect;
    int showingDirTurns;
    int id;
    int maxSpeed = MAX_SPEED;
    int minSpeed = 0;
    boolean destroying = false;
    int destroyingTurns = 0;

    public enum SpeedLimitType {
        MAX_SPEED,
        MIN_SPEED
    }

    public Locomotive(int id, String aspect) {
        this.id = id;
        this.aspect = aspect;
        resetTurns();
    }

    public Locomotive(int id, char c) {
        this(id, "" + c);
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
        visitor.visitLocomotive(this);
    }

    public String getAspect() {
        return aspect;
    }

    public void update() {
        if (isDestroying()) {
            return;
        }

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
            updateLimitedSpeed();
            return true;
        }
        return false;
    }

    public void updateLimitedSpeed() {
        if (speedChangeReluctance > 0) {
            speedChangeReluctance--;
            return;
        }
        speedChangeReluctance = SPEED_CHANGE_MAX_RELUCTANCE;
        if (getSpeed() > getMaxSpeed()) {
            decSpeed();
        } else if (getSpeed() < getMinSpeed()) {
            incSpeed();
        }
    }

    public void resetTurnsIfNeeded() {
        if (turns == -1) {
            resetTurns();
        }
    }

    public void resetTurns() {
        this.turns = speed == 0 ? -1 : 50 / speed;
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

    public int getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(int maxSpeed) {
        this.maxSpeed = maxSpeed;
        this.minSpeed = 0;
    }

    public int getMinSpeed() {
        return minSpeed;
    }

    public void setMinSpeed(int minspeed) {
        this.minSpeed = minspeed;
        this.maxSpeed = MAX_SPEED;
    }

    @Override
    public void destroy() {
        this.destroying = true;
        this.destroyingTurns = 1000;
    }

    @Override
    public boolean isDestroying() {
        return this.destroying;
    }

    @Override
    public boolean isDestroyed() {
        if (isDestroying() && destroyingTurns-- <= 0) {
            return true;
        }
        return false;
    }

}
