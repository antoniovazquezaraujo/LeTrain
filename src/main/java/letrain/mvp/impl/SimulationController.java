package letrain.mvp.impl;

import letrain.audio.AudioController;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.mvp.impl.services.SimulationService;

/**
 * Centralizes the simulation logic that runs on every game tick (approx 20 TPS).
 * This ensures that both 2D and 3D views behave identically regarding physics and logic.
 */
public class SimulationController {
    private final letrain.mvp.Model model;
    private final AudioController audioController;
    private final RailTrackMaker trackMaker;
    private final SimulationService simulationService;

    public SimulationController(letrain.mvp.Model model, AudioController audioController, RailTrackMaker trackMaker) {
        this.model = model;
        this.audioController = audioController;
        this.trackMaker = trackMaker;
        this.simulationService = new SimulationService(model);
    }

    /**
     * Performs one simulation tick.
     */
    public void tick() {
        // 1. Progress track construction if active
        if (trackMaker != null) {
            trackMaker.makeTracks();
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

        // 6. Update semaphores: red if next segment occupied, green if free
        updateSemaphores();
    }

    private void updateSemaphores() {
        letrain.core.segments.RailwayGraph graph = model.getRailwayGraph();
        letrain.core.segments.BlockManager bm = model.getBlockManager();
        if (graph == null || bm == null) return;

        for (letrain.track.RailSemaphore sem : model.getSemaphores()) {
            letrain.track.Track track = model.getRailMap().getTrackAt(sem.getPosition());
            if (track == null) continue;

            letrain.map.Dir dir = sem.getCreationDir();
            letrain.track.Track nextTrack = track.getConnected(dir);
            if (nextTrack instanceof letrain.track.rail.RailTrack) {
                letrain.core.segments.Segment seg = graph.getSegment((letrain.track.rail.RailTrack) nextTrack);
                if (seg != null) {
                    boolean occupied = !bm.getOwners(seg).isEmpty();
                    sem.setOpen(!occupied);
                }
            }
        }
    }
}
