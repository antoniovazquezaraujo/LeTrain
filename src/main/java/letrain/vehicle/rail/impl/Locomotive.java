package letrain.vehicle.rail.impl;

import letrain.map.Dir;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A locomotive (engine) that pulls or pushes a train. Implements {@link Tractor}
 * for speed control, inertia, and turn-based movement timing.
 *
 * <p>Key concepts:
 * <ul>
 *   <li>{@code currentSpeed} (0-10) — actual visual/acoustic speed</li>
 *   <li>{@code targetSpeed} (0-10) — desired speed set by player</li>
 *   <li>{@code turns} — countdown ticks between cell advances
 *       ({@code 50 / currentSpeed})</li>
 *   <li>{@code stalled} — frozen after collision; must reverse to recover</li>
 * </ul>
 */
public class Locomotive extends Linker implements Tractor {
    private static final Logger log = LoggerFactory.getLogger(Locomotive.class);
    private static final int MAX_DESTROY_TURNS = 200;
    private static final long serialVersionUID = 1L;
    /** Maximum speed in game units (notches 0-10). */
    public static final int MAX_SPEED = 10;

    static final int SPEED_CHANGE_MAX_RELUCTANCE = 2;
    int currentSpeed;
    int targetSpeed;
    int railsSinceLastSpeedChange = 0;
    int previousSpeed = 0;
    int distanceTraveled = 0;
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
    private boolean engineExplicitlyOff = false;

    public enum SpeedLimitType {
        MAX_SPEED,
        MIN_SPEED
    }

    public static final String[] COLOR_PALETTE = {
        "WHITE", // 0
        "RED_BRIGHT", // 1
        "GREEN_BRIGHT", // 2
        "YELLOW_BRIGHT", // 3
        "BLUE_BRIGHT", // 4
        "MAGENTA_BRIGHT", // 5
        "CYAN_BRIGHT", // 6
        "GRAY", // 7
        "ORANGE", // 8
        "PINK" // 9
    };
    private static final java.util.Random RANDOM = new java.util.Random();

    public static String pickRandomColor() {
        return COLOR_PALETTE[RANDOM.nextInt(COLOR_PALETTE.length)];
    }

    private String color = "WHITE";

    public Locomotive(int id, String aspect) {
        this(id, aspect, pickRandomColor());
    }

    public Locomotive(int id, String aspect, String color) {
        this.id = id;
        this.aspect = aspect;
        this.color = (color != null && !color.isBlank()) ? color : pickRandomColor();
        this.currentSpeed = 0;
        this.targetSpeed = 0;
        resetTurns();
    }

    protected Locomotive() {}

    public Locomotive(int id, char c) {
        this(id, "" + c, pickRandomColor());
    }

    public String getColor() {
        return this.color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getId() {
        return this.id;
    }

    @Override
    public void toggleReversed() {
        if (currentSpeed > 0) {
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
            getTrain().getMovementManager().refreshLinkersDirection();
            getTrain().notifySenseChanged(!isReversed());
            if (getTrain().getSafetyManager() != null && getTrain().getModel() != null) {
                getTrain().getSafetyManager().onReverse();
            }
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

    /**
     * Main update tick. Handles acoustic signals, inertia, turn consumption,
     * and triggers {@link Train#advance()} when it's time to move.
     *
     * @return true if the train moved this tick
     */
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
            if (getTrain() != null && getTrain().getLogisticsManager().isLoading()) {
                return moved;
            }

            // Handle acceleration from 0 - allows getting unstuck from speed 0
            if (currentSpeed == 0 && targetSpeed > 0) {
                boolean blocked = false;
                if (getTrain() != null) {
                    java.util.Deque<Linker> linkers = getTrain().getLinkers();
                    if (!linkers.isEmpty()) {
                        boolean normalSense = getTrain().getDirectorLinker() == null
                                || !getTrain().getDirectorLinker().isReversed();
                        Linker head = normalSense ? linkers.getFirst() : linkers.getLast();
                        if (head != null && head.getTrack() != null) {
                            letrain.track.Track nextTrack = head.getTrack().getConnected(head.getDir());
                            if (nextTrack != null) {
                                Linker occupant = nextTrack.getLinker();
                                if (occupant != null
                                        && occupant.getTrain() != null
                                        && occupant.getTrain() != getTrain()) {
                                    blocked = true;
                                    log.info(
                                            "[CONTACT-TIMING] Instant contact check on start: next cell is occupied. Firing contact sound immediately.");
                                    getTrain().notifyContact(nextTrack.getPosition(), targetSpeed);

                                    // Stop the train immediately
                                    setCurrentSpeed(0);
                                    setTargetSpeed(0);
                                    this.turns = -1;
                                    this.totalTurns = -1;
                                }
                            }
                        }
                    }
                }

                if (!blocked) {
                    updateInertia();
                    resetTurns();
                }
            }

            if (currentSpeed > 0) {
                consumeTurn();
            }

            if (isTimeToMove()) {
                boolean hasPerm =
                        getTrain() != null && getTrain().getSafetyManager().hasPermissionToMove();
                log.debug(
                        "Locomotive {}: isTimeToMove=true, currentSpeed={}, targetSpeed={}, turns={}, hasPermissionToMove={}",
                        id,
                        currentSpeed,
                        targetSpeed,
                        turns,
                        hasPerm);
                if (getTrain().getMovementManager().advance()) {
                    log.debug("Locomotive {}: advance() succeeded", id);
                    moved = true;
                    incDistanceTraveled();

                    updateInertia();
                    resetTurns();
                    updateLimitedSpeed();
                    this.previousSpeed = this.currentSpeed;
                    // Sincronizar resets de animación en otras locomotoras
                    getTrain().getTractors().stream()
                            .filter(t -> t instanceof Locomotive && t != this)
                            .forEach(t -> ((Locomotive) t).resetTurns());
                } else if (getTrain() == null || !getTrain().isAutoMode()) {
                    log.debug("Locomotive {}: advance() failed (manual mode or null train). Setting speed to 0", id);
                    // Blocked/Collision — stop the train (only in manual mode)
                    setCurrentSpeed(0);
                    setTargetSpeed(0);
                } else {
                    log.debug("Locomotive {}: advance() failed (AUTO mode). Letting inertia brake.", id);
                    // Auto mode: don't punish, but let inertia brake

                    updateInertia();
                    resetTurns();
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
                if (getTrain() != null && getTrain().isPendingManualMode()) {
                    log.info("Locomotive {}: train fully stopped, switching to manual mode.", id);
                    getTrain().setAutoMode(false);
                    getTrain().setPendingManualMode(false);
                }
            }
            return;
        }

        railsSinceLastSpeedChange++;

        // Factor de inercia fallback only if audio is disabled
        int factor = isBraking() ? 1 : 2;
        int neededRails = Math.max(1, currentSpeed * factor);

        if (railsSinceLastSpeedChange >= neededRails) {
            int oldSpeed = currentSpeed;
            if (currentSpeed < targetSpeed) {
                setCurrentSpeed(currentSpeed + 1);
            } else {
                setCurrentSpeed(currentSpeed - 1);
            }
            log.info(
                    "Locomotive {}: inertia speed update from {} to {} (targetSpeed={})",
                    id,
                    oldSpeed,
                    currentSpeed,
                    targetSpeed);
            railsSinceLastSpeedChange = 0;
        }
    }

    public boolean isBraking() {
        return currentSpeed > targetSpeed && currentSpeed > 0;
    }

    public boolean isEngineOn() {
        return engineOn;
    }

    public void setEngineOn(boolean on) {
        this.engineOn = on;
        this.engineExplicitlyOff = !on;
    }

    private boolean forceIdleSound = false;

    public void setForceIdleSound(boolean force) {
        this.forceIdleSound = force;
    }

    public boolean isForceIdleSound() {
        return forceIdleSound;
    }

    public void incSpeed() {
        // Do not increase speed while stalled from a collision.
        if (getTrain() != null && getTrain().isStalled()) {
            return;
        }
        // Speed change in auto mode → switch to manual
        if (getTrain() != null && getTrain().isAutoMode()) {
            getTrain().setAutoMode(false);
        }
        if (getTrain() != null) {
            getTrain().setSpeed(this.targetSpeed + 1);
        } else {
            setTargetSpeed(this.targetSpeed + 1);
        }
    }

    public void decSpeed() {
        // Speed change in auto mode → switch to manual
        if (getTrain() != null && getTrain().isAutoMode()) {
            getTrain().setAutoMode(false);
        }
        if (getTrain() != null) {
            getTrain().setSpeed(this.targetSpeed - 1);
        } else {
            setTargetSpeed(this.targetSpeed - 1);
        }
    }

    public void setTargetSpeed(int speed) {
        if (getTrain() != null
                && getTrain().getSafetyManager() != null
                && getTrain().getSafetyManager().isWaitingForBlock()) {
            if (speed > 0) {
                log.info(
                        "Locomotive {}: Train is waiting for block. Intercepting setTargetSpeed({}) and saving it instead.",
                        id,
                        speed);
                getTrain().setSavedTargetSpeed(speed);
                speed = 0;
            }
        }
        if (this.targetSpeed != speed) {
            log.info("Locomotive {}: setTargetSpeed changes from {} to {}", id, this.targetSpeed, speed);
        }
        int oldSpeed = this.targetSpeed;
        this.targetSpeed = speed;
        if (this.targetSpeed > 0) {
            if (!engineExplicitlyOff) {
                this.engineOn = true;
            }
            if (getTrain() != null) {
                getTrain().setStalled(false);
            }
        }
        limitTargetSpeed();

        // Sincronizar con el resto de locomotoras del tren
        if (getTrain() != null) {
            for (Tractor tractor : getTrain().getTractors()) {
                if (tractor instanceof Locomotive && tractor != this) {

                    if (tractor.getTargetSpeed() != this.targetSpeed) {
                        ((Locomotive) tractor).setTargetSpeed(this.targetSpeed);
                    }
                }
            }
        }
    }

    @Override
    public int getTargetSpeed() {
        return this.targetSpeed;
    }

    @Override
    public void setSpeed(int speed) {
        setTargetSpeed(speed);
    }

    @Override
    public void setCurrentSpeed(int speed) {
        int effectiveSpeed = speed;
        if (this.currentSpeed == effectiveSpeed) {
            return;
        }
        this.currentSpeed = effectiveSpeed;
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
