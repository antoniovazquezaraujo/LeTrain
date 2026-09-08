package letrain.mvp.impl;

import letrain.audio.AudioController;
import letrain.mvp.impl.services.SimulationService;
import letrain.vehicle.rail.impl.Locomotive;

/**
 * Centralizes the simulation logic that runs on every game tick (approx 20 TPS). This ensures that
 * both 2D and 3D views behave identically regarding physics and logic.
 */
public class SimulationController {
    private final letrain.mvp.Model model;
    private final AudioController audioController;
    private final RailTrackMaker trackMaker;
    private final SimulationService simulationService;

    public SimulationController(letrain.mvp.Model model, AudioController audioController,
            RailTrackMaker trackMaker) {
        this.model = model;
        this.audioController = audioController;
        this.trackMaker = trackMaker;
        this.simulationService = new SimulationService(model);
    }

    /** Performs one simulation tick. */
    public void tick() {
        // Paused editing (ADR-020): while in a paused editing mode the world is frozen but track
        // construction keeps running (and is made instantaneous by the track maker).
        boolean paused = model.isSimulationPaused();

        // 0. Update the scheduler
        if (!paused && model.getScheduler() != null) {
            model.getScheduler().tick();
        }

        // 1. Progress track construction if active (always, even while paused so editing works)
        if (trackMaker != null) {
            trackMaker.makeTracks();
        }

        if (paused) {
            return;
        }

        // 2. Move all vehicles
        simulationService.moveVehicles();

        // 3. Handle sound for destroying locomotives (before they are removed)
        for (Locomotive loco : model.getLocomotives()) {
            if (loco.isDestroying()) {
                audioController.stopSynthesizer(loco.getId());
            }
        }

        // 4. Load and unload trains at stations
        simulationService.handleIndustrialActions();

        // 5. Cleanup destroyed trains
        simulationService.cleanupEntities();
    }
}
