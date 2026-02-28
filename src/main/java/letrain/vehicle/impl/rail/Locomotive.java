package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.visitor.Visitor;

public class Locomotive extends Linker implements Tractor {
    private static final int MAX_DESTROY_TURNS = 400;
    private static final long serialVersionUID = 1L;
    final static int MAX_SPEED = 10;
    final static int SPEED_CHANGE_MAX_RELUCTANCE = 2;
    int currentSpeed;
    int targetSpeed;
    int railsSinceLastSpeedChange = 0;
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
        Dir pushDir = getDir();
        Track nextTrack = getTrack();
        setDir(nextTrack.getDir(pushDir));
        setReversed(!isReversed());
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
            // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
            if (getTrain() != null && getTrain().isLoading()) {
                return moved;
            }

            // Inhibit movement if the engine is just starting sound-wise
            if (engineStarting) {
                return moved;
            }

            // Handle acceleration from 0 - allows getting unstuck from speed 0
            if (currentSpeed == 0 && targetSpeed > 0) {
                updateInertia();
                resetTurns();
            }

            if (isTimeToMove()) {
                if (getTrain().advance()) {
                    moved = true;
                    incDistanceTraveled();
                    updateInertia();
                    resetTurns();
                    updateLimitedSpeed();
                    // Sincronizar resets de animación en otras locomotoras
                    getTrain().getTractors().stream()
                            .filter(t -> t instanceof Locomotive && t != this)
                            .forEach(t -> ((Locomotive) t).resetTurns());
                } else {
                    // Blocked/Collision - Stop the train
                    setCurrentSpeed(0);
                    setTargetSpeed(0);
                }
            } else {
                consumeTurn();
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
            return;
        }

        railsSinceLastSpeedChange++;

        // Factor de inercia: 2 raíles por cada punto de velocidad actual (acelerando)
        // O 1 raíl por cada punto si está frenando (frena más rápido).
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
        limitCurrentSpeed();
        resetTurns(); // Force reset to ensure turns are synchronized with speed 0 immediately
        if (getTrain() != null) {
            getTrain().notifySpeedChanged(this.currentSpeed);
            // Sincronizar con el resto de locomotoras del tren
            for (Tractor tractor : getTrain().getTractors()) {
                if (tractor instanceof Locomotive && tractor != this) {
                    ((Locomotive) tractor).setCurrentSpeed(this.currentSpeed);
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
        this.turns = currentSpeed == 0 ? -1 : 50 / currentSpeed;
        this.totalTurns = this.turns;
    }

    public int getTotalTurns() {
        return totalTurns;
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
        if (isDestroying() && destroyingTurns-- <= 0) {
            return true;
        }
        return false;
    }

    @Override
    public int getDistanceTraveled() {
        return distanceTraveled;
    }

    @Override
    public void incDistanceTraveled() {
        distanceTraveled++;
    }

}
