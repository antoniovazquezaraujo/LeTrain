package letrain.mvp;

public interface Presenter extends GameViewListener {

    enum TrackType {
        NORMAL_TRACK, STATION_TRACK, TUNNEL_TRACK, TUNNEL_GATE_TRACK, BRIDGE_TRACK, BRIDGE_GATE_TRACK
    }

    View getView();

    Model getModel();

    letrain.audio.AudioController getAudioController();

    /**
     * The paused-editing undo/redo session (ADR-020 item 3), owned by the presenter so it survives
     * the model swaps an undo performs. Returns null while no editing session is active (e.g. the
     * terminal presenter when pause-editing is off). A presenter that never records an edit history
     * may simply not override this.
     */
    default letrain.command.UndoRedoHistory getUndoRedoHistory() {
        return null;
    }
}
