package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.visitor.Visitor;

public class Locomotive extends Linker implements Tractor {
    private static final int MAX_DESTROY_TURNS = 200;
    private static final long serialVersionUID = 1L;
    final static int MAX_SPEED = 10;
    final static int SPEED_CHANGE_MAX_RELUCTANCE = 2;
    int currentSpeed;
    int targetSpeed;
    int railsSinceLastSpeedChange = 0;
    int previousSpeed = 0;
    int distanceTraveled = 0;
    boolean engineStarting = false;
    int turns;
    int totalTurns;
    private String aspect;
    int showingDirTurns;
    int id;
    int maxSpeed = MAX_SPEED;
    int minSpeed = 0;
    boolean destroying = false;
    int destroyingTurns = 0;
    boolean engineOn = false;

    public enum SpeedLimitType {
        MAX_SPEED,
        MIN_SPEED
    }

    public Locomotive(int id, String aspect) {
        this.id = id;
        this.aspect = aspect;
        this.currentSpeed = 0;
        this.targetSpeed = 0;
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
        if (currentSpeed > 0) {
            // No permitir invertir marcha en movimiento (opcional, pero realista)
            // Por ahora solo ponemos targetSpeed a 0
            setTargetSpeed(0);
            return;
        }
        Dir currentDir = getDir();
        Dir currentEntry = getEntryDir();
        setEntryDir(currentDir);
        setDir(currentEntry);
        setReversed(!isReversed());
        if (getTrain() != null) {
            getTrain().setStalled(false);
        }
        showingDirTurns = 5;
        if (getTrain() != null) {
            getTrain().refreshLinkersDirection();
            getTrain().notifySenseChanged(!isReversed());
        }
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

    public boolean isTimeToMove() {
        if (this.turns == 0) {
            return true;
        }
        return false;
    }

    public boolean update() {
        boolean moved = false;
        if (isDestroying()) {
            return moved;
        }

        if (isDirectorLinker()) {
            // Motor apagado: no puede moverse
            if (!engineOn) {
                return moved;
            }

            // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
            if (getTrain() != null && getTrain().isLoading()) {
                return moved;
            }

            // Inhibit movement if the engine is just starting sound-wise
            if (engineStarting) {
                return moved;
            }

            // We apply sound-driven speed primarily on rail boundaries to avoid visual jump
            // glitches.
            // But if we are parked at 0, we can safely apply it immediately to jumpstart
            // the motor.
            if (currentSpeed == 0 && acousticSpeedSignal != -1) {
                if (currentSpeed != acousticSpeedSignal) {
                    setCurrentSpeed(acousticSpeedSignal);
                }
                acousticSpeedSignal = -1;
            }

            // Handle acceleration from 0 - allows getting unstuck from speed 0
            if (currentSpeed == 0 && targetSpeed > 0) {
                updateInertia();
                resetTurns();
            }

            if (currentSpeed > 0) {
                consumeTurn();
            }

            if (isTimeToMove()) {
                if (getTrain().advance()) {
                    moved = true;
                    incDistanceTraveled();

                    // Apply mid-movement acoustic gear shifts exactly on rail boundaries
                    // to prevent visual interpolation snapping (jumping backwards)
                    if (acousticSpeedSignal != -1) {
                        if (currentSpeed != acousticSpeedSignal) {
                            setCurrentSpeed(acousticSpeedSignal);
                        }
                        acousticSpeedSignal = -1;
                    }

                    updateInertia();
                    resetTurns();
                    updateLimitedSpeed();
                    this.previousSpeed = this.currentSpeed;
                    // Sincronizar resets de animación en otras locomotoras
                    getTrain().getTractors().stream()
                            .filter(t -> t instanceof Locomotive && t != this)
                            .forEach(t -> ((Locomotive) t).resetTurns());
                } else {
                    // Blocked/Collision - Stop the train
                    setCurrentSpeed(0);
                    setTargetSpeed(0);
                }
            }
        } else {
            // No somos el director, pero consumimos turnos para animación suave
            consumeTurn();
        }
        return moved;
    }

    private void updateInertia() {
        if (currentSpeed == targetSpeed) {
            railsSinceLastSpeedChange = 0;
            if (currentSpeed == 0) {
                setRailsSinceStop(0);
            }
            return;
        }

        railsSinceLastSpeedChange++;

        // When engine transitions are active, we relinquish speed control to the Audio
        // Gear Syncer
        if (engineTransitioning) {
            railsSinceLastSpeedChange = 0;
            return;
        }

        // Factor de inercia fallback only if audio is disabled
        int factor = isBraking() ? 1 : 2;
        int neededRails = Math.max(1, currentSpeed * factor);

        if (railsSinceLastSpeedChange >= neededRails) {
            if (currentSpeed < targetSpeed) {
                setCurrentSpeed(currentSpeed + 1);
            } else {
                setCurrentSpeed(currentSpeed - 1);
            }
            railsSinceLastSpeedChange = 0;
        }
    }

    public boolean isBraking() {
        return currentSpeed > targetSpeed && currentSpeed > 0;
    }

    public void setEngineStarting(boolean starting) {
        this.engineStarting = starting;
    }

    public boolean isEngineStarting() {
        return engineStarting;
    }

    public boolean isEngineOn() {
        return engineOn;
    }

    public void setEngineOn(boolean on) {
        this.engineOn = on;
    }

    private boolean engineTransitioning = false;

    public void setEngineTransitioning(boolean transitioning) {
        this.engineTransitioning = transitioning;
    }

    public boolean isEngineTransitioning() {
        return engineTransitioning;
    }

    private volatile int acousticSpeedSignal = -1;

    public void setAcousticSpeedSignal(int notch) {
        this.acousticSpeedSignal = notch;
    }

    public void incSpeed() {
        setTargetSpeed(this.targetSpeed + 1);
    }

    public void decSpeed() {
        setTargetSpeed(this.targetSpeed - 1);
    }

    public void setTargetSpeed(int speed) {
        if (this.targetSpeed == speed) {
            return;
        }
        this.targetSpeed = speed;
        if (this.targetSpeed > 0 && getTrain() != null) {
            getTrain().setStalled(false);
        }
        limitTargetSpeed();

        // Sincronizar con el resto de locomotoras del tren
        if (getTrain() != null) {
            for (Tractor tractor : getTrain().getTractors()) {
                if (tractor instanceof Locomotive && tractor != this) {
                    ((Locomotive) tractor).setTargetSpeed(this.targetSpeed);
                }
            }
        }
    }

    @Override
    public void setSpeed(int speed) {
        setTargetSpeed(speed);
    }

    public void setCurrentSpeed(int speed) {
        if (this.currentSpeed == speed) {
            return;
        }
        this.currentSpeed = speed;
        if (this.currentSpeed == 0) {
            // We allow the renderer to finish the last move before resetting in the next tick
        }
        limitCurrentSpeed();
        resetTurns();

        if (getTrain() != null) {
            getTrain().notifySpeedChanged(this.currentSpeed);
            // Sincronizar con el resto de locomotoras del tren
            for (Tractor tractor : getTrain().getTractors()) {
                if (tractor instanceof Locomotive && tractor != this) {
                    Locomotive other = (Locomotive) tractor;
                    if (other.currentSpeed != this.currentSpeed) {
                        other.setCurrentSpeed(this.currentSpeed);
                    }
                }
            }
        }
    }

    // Mantener getSpeed para compatibilidad con el resto del sistema
    public int getSpeed() {
        return this.currentSpeed;
    }

    public int getTargetSpeed() {
        return this.targetSpeed;
    }

    private void limitTargetSpeed() {
        if (this.targetSpeed > MAX_SPEED) {
            this.targetSpeed = MAX_SPEED;
        }
        if (this.targetSpeed < 0) {
            this.targetSpeed = 0;
        }
    }

    private void limitCurrentSpeed() {
        if (this.currentSpeed > MAX_SPEED) {
            this.currentSpeed = MAX_SPEED;
        }
        if (this.currentSpeed < 0) {
            this.currentSpeed = 0;
        }
    }

    public void updateLimitedSpeed() {
        // speedChangeReluctance ya no es tan necesario con la inercia por raíles,
        // pero lo mantenemos si queremos limitar el target.
        if (targetSpeed > getMaxSpeed()) {
            setTargetSpeed(getMaxSpeed());
        } else if (targetSpeed < getMinSpeed()) {
            setTargetSpeed(getMinSpeed());
        }
    }

    public void resetTurnsIfNeeded() {
        if (turns == -1) {
            resetTurns();
        }
    }

    public void resetTurns() {
        this.turns = currentSpeed <= 0 ? -1 : 50 / currentSpeed;
        this.totalTurns = this.turns;
    }

    public int getTotalTurns() {
        return totalTurns;
    }

    public int getPreviousSpeed() {
        return previousSpeed;
    }

    public int getTurns() {
        return turns;
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
        this.destroyingTurns = MAX_DESTROY_TURNS;
    }

    @Override
    public boolean isDestroying() {
        return this.destroying;
    }

    @Override
    public boolean isDestroyed() {
        return isDestroying() && destroyingTurns <= 0;
    }

    public void updateDestroyTimer() {
        if (isDestroying() && destroyingTurns > 0) {
            destroyingTurns--;
        }
    }

    @Override
    public int getDistanceTraveled() {
        return distanceTraveled;
    }

    @Override
    public void incDistanceTraveled() {
        distanceTraveled++;
    }

    public int getRailsSinceLastSpeedChange() {
        return railsSinceLastSpeedChange;
    }

    public void setStalled(boolean stalled) {
        if (getTrain() != null) {
            getTrain().setStalled(stalled);
        }
    }

    public boolean isStalled() {
        return getTrain() != null && getTrain().isStalled();
    }
}
