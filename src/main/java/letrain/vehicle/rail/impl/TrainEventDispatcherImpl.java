package letrain.vehicle.rail.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import letrain.map.Point;
import letrain.utils.SerializationHelper;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.TrainEventDispatcher;
import letrain.vehicle.rail.TrainEventListener;

/**
 * Manages event listener registrations and broadcasts train events.
 */
public class TrainEventDispatcherImpl implements TrainEventDispatcher {
    private final Train train;
    private List<ScriptTrainEventListener> scriptTrainListeners;
    private List<CoreTrainEventListener> coreTrainListeners;

    public TrainEventDispatcherImpl(Train train) {
        this.train = train;
        this.scriptTrainListeners = new CopyOnWriteArrayList<>();
        this.coreTrainListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public List<ScriptTrainEventListener> getScriptTrainListeners() {
        if (scriptTrainListeners == null) {
            scriptTrainListeners = new CopyOnWriteArrayList<>();
        }
        return Collections.unmodifiableList(scriptTrainListeners);
    }

    @Override
    public List<CoreTrainEventListener> getCoreTrainListeners() {
        if (coreTrainListeners == null) {
            coreTrainListeners = new CopyOnWriteArrayList<>();
        }
        return Collections.unmodifiableList(coreTrainListeners);
    }

    @Override
    public void addScriptTrainEventListener(ScriptTrainEventListener listener) {
        if (scriptTrainListeners == null) {
            scriptTrainListeners = new CopyOnWriteArrayList<>();
        }
        scriptTrainListeners.add(listener);
    }

    @Override
    public void removeScriptTrainEventListener(ScriptTrainEventListener listener) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.remove(listener);
        }
    }

    @Override
    public void addCoreTrainEventListener(CoreTrainEventListener listener) {
        if (coreTrainListeners == null) {
            coreTrainListeners = new CopyOnWriteArrayList<>();
        }
        coreTrainListeners.add(listener);
    }

    @Override
    public void removeCoreTrainEventListener(CoreTrainEventListener listener) {
        if (coreTrainListeners != null) {
            coreTrainListeners.remove(listener);
        }
    }

    @Override
    public void removeAllScriptTrainEventListeners() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.clear();
        }
    }

    @Override
    public void postLoadInit() {
        scriptTrainListeners = SerializationHelper.ensureListInitializedConcurrent(scriptTrainListeners);
        coreTrainListeners = SerializationHelper.ensureListInitializedConcurrent(coreTrainListeners);
    }

    @Override
    public void notifySpeedChanged(int speed) {
        for (TrainEventListener l : scriptTrainListeners) {
            l.onSpeedChanged(speed);
        }
        for (TrainEventListener l : coreTrainListeners) {
            l.onSpeedChanged(speed);
        }
    }

    @Override
    public void notifySenseChanged(boolean forward) {
        for (TrainEventListener l : scriptTrainListeners) {
            l.onSenseChanged(forward);
        }
        for (TrainEventListener l : coreTrainListeners) {
            l.onSenseChanged(forward);
        }
    }

    @Override
    public void notifyLink() {
        scriptTrainListeners.forEach(l -> l.onLink(train));
        coreTrainListeners.forEach(l -> l.onLink(train));
    }

    @Override
    public void notifyUnlink() {
        scriptTrainListeners.forEach(l -> l.onUnlink(train));
        coreTrainListeners.forEach(l -> l.onUnlink(train));
    }

    @Override
    public void notifyEnterSensor(boolean isForward) {
        scriptTrainListeners.forEach(l -> l.onSensorEnter(train, isForward));
        coreTrainListeners.forEach(l -> l.onSensorEnter(train, isForward));
    }

    @Override
    public void notifyExitSensor(boolean isForward) {
        scriptTrainListeners.forEach(l -> l.onSensorExit(train, isForward));
        coreTrainListeners.forEach(l -> l.onSensorExit(train, isForward));
    }

    @Override
    public void notifyContact(Point pos, int speed) {
        for (TrainEventListener l : scriptTrainListeners) {
            l.onContact(train, pos, speed);
        }
        for (TrainEventListener l : coreTrainListeners) {
            l.onContact(train, pos, speed);
        }
    }

    @Override
    public void notifyCrash(Point pos, int speed) {
        for (TrainEventListener l : scriptTrainListeners) {
            l.onCrash(train, pos, speed);
        }
        for (TrainEventListener l : coreTrainListeners) {
            l.onCrash(train, pos, speed);
        }
    }
}
