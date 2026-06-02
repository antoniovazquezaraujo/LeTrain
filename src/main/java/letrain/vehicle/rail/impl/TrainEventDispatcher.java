package letrain.vehicle.rail.impl;

import letrain.vehicle.rail.TrainEventListener;
import letrain.track.Sensor;
import letrain.segments.Segment;
import letrain.map.Point;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import letrain.utils.SerializationHelper;

/**
 * Manages event listener registrations and broadcasts train events.
 */
public class TrainEventDispatcher {
    private final Train train;
    private List<TrainEventListener> scriptTrainListeners;
    private List<TrainEventListener> coreTrainListeners;

    public TrainEventDispatcher(Train train) {
        this.train = train;
        this.scriptTrainListeners = new CopyOnWriteArrayList<>();
        this.coreTrainListeners = new CopyOnWriteArrayList<>();
    }

    public List<TrainEventListener> getScriptTrainListeners() {
        if (scriptTrainListeners == null) {
            scriptTrainListeners = new CopyOnWriteArrayList<>();
        }
        return scriptTrainListeners;
    }

    public List<TrainEventListener> getCoreTrainListeners() {
        if (coreTrainListeners == null) {
            coreTrainListeners = new CopyOnWriteArrayList<>();
        }
        return coreTrainListeners;
    }

    public void addScriptTrainEventListener(TrainEventListener listener) {
        getScriptTrainListeners().add(listener);
    }

    public void removeScriptTrainEventListener(TrainEventListener listener) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.remove(listener);
        }
    }

    public void addCoreTrainEventListener(TrainEventListener listener) {
        getCoreTrainListeners().add(listener);
    }

    public void removeCoreTrainEventListener(TrainEventListener listener) {
        if (coreTrainListeners != null) {
            coreTrainListeners.remove(listener);
        }
    }

    public void removeAllScriptTrainEventListeners() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.clear();
        }
    }

    public void postLoadInit() {
        scriptTrainListeners = SerializationHelper.ensureListInitializedConcurrent(scriptTrainListeners);
        coreTrainListeners = SerializationHelper.ensureListInitializedConcurrent(coreTrainListeners);
    }

    public void notifySpeedChanged(int speed) {
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onSpeedChanged(speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onSpeedChanged(speed);
            }
        }
    }

    public void notifySenseChanged(boolean forward) {
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onSenseChanged(forward);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onSenseChanged(forward);
            }
        }
    }

    public void notifyLink() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> {
                l.onLink(train);
            });
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> {
                l.onLink(train);
            });
        }
    }

    public void notifyUnlink() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> {
                l.onUnlink(train);
            });
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> {
                l.onUnlink(train);
            });
        }
    }

    public void notifyEnterSensor(Sensor sensor, boolean isForward) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorEnter(train, isForward);
                }
            });
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorEnter(train, isForward);
                }
            });
        }
    }

    public void notifyExitSensor(Sensor sensor, boolean isForward) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorExit(train, isForward);
                }
            });
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorExit(train, isForward);
                }
            });
        }
    }

    public void notifySegmentOccupied(Segment segment) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> {
                l.onSegmentOccupied(train, segment);
            });
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> {
                l.onSegmentOccupied(train, segment);
            });
        }
    }

    public void notifyContact(Point pos, int speed) {
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onContact(train, pos, speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onContact(train, pos, speed);
            }
        }
    }

    public void notifyCrash(Point pos, int speed) {
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onCrash(train, pos, speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onCrash(train, pos, speed);
            }
        }
    }
}
