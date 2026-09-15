package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.ForkEventListener;
import letrain.track.RailSemaphore;
import letrain.track.SemaphoreEventListener;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.Station;
import letrain.track.StationEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;

/**
 * Encapsulates the registration and re-establishment of system event listeners for sensors, forks,
 * stations, semaphores, and trains.
 */
@JsonIgnoreType
public final class ModelListenerService {

    private ModelListenerService() {}

    public static void setupSensorSystemListeners(Model model, Sensor sensor) {
        final int id = sensor.getId();
        sensor.addSystemSensorEventListener(new SensorEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                model.getEventLogManager().addEntry("Train " + train.getId() + " entered Sensor "
                        + id + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " exited Sensor " + id);
            }
        });
    }

    public static void setupForkSystemListeners(Model model, ForkRailTrack fork) {
        final int id = fork.getId();
        fork.addSystemForkEventListener(new ForkEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                model.getEventLogManager().addEntry("Train " + train.getId() + " entered Fork " + id
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onDirectionChanged(boolean normal) {
                model.getEventLogManager()
                        .addEntry("Fork " + id + " set to " + (normal ? "Normal" : "Alternative"));
                if (model.getLocomotives() != null) {
                    for (Locomotive loco : model.getLocomotives()) {
                        if (loco.getTrain() != null) {
                            loco.getTrain().resetSafetyTimer();
                        }
                    }
                }
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " exited Fork " + id);
            }
        });
    }

    public static void setupSemaphoreSystemListeners(Model model, RailSemaphore semaphore) {
        final int id = semaphore.getId();
        semaphore.addSystemSemaphoreEventListener(new SemaphoreEventListener() {
            @Override
            public void onOpen() {
                model.getEventLogManager().addEntry("Semaphore " + id + " opened");
            }

            @Override
            public void onClosed() {
                model.getEventLogManager().addEntry("Semaphore " + id + " closed");
            }

            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                model.getEventLogManager().addEntry("Train " + train.getId() + " entered Semaphore "
                        + id + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " exited Semaphore " + id);
            }
        });
    }

    public static void setupStationSystemListeners(Model model, Station station) {
        final int id = station.getId();
        station.addSystemStationEventListener(new StationEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " entered Station " + id);
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " exited Station " + id);
            }

            @Override
            public void onLoad(Train train) {}

            @Override
            public void onUnload(Train train) {}

            @Override
            public void onStartLoad(Train train) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " starting Load at Station " + id);
            }

            @Override
            public void onEndLoad(Train train) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " ended Load at Station " + id);
            }

            @Override
            public void onStartUnload(Train train) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " starting Unload at Station " + id);
            }

            @Override
            public void onEndUnload(Train train) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " ended Unload at Station " + id);
            }
        });
    }

    public static CoreTrainEventListener createCoreTrainEventListener(Model model) {
        return new CoreTrainEventListener() {
            @Override
            public void onCrash(Train train, Point pos, int speed) {
                model.getEventLogManager().addEntry("CRASH! Train " + train.getId() + " crashed!");
                model.getEconomyManager().onTrainCrashed(train);
            }

            @Override
            public void onContact(Train train, Point pos, int speed) {
                model.getEventLogManager()
                        .addEntry("Train " + train.getId() + " contact (speed=" + speed + ")");
            }

            @Override
            public void onLink(Train train) {
                model.getEventLogManager().addEntry("Train " + train.getId() + " linked");
            }

            @Override
            public void onUnlink(Train train) {
                model.getEventLogManager().addEntry("Train " + train.getId() + " unlinked");
            }
        };
    }

    public static void reestablishSystemListeners(Model model) {
        if (model.getSensors() != null) {
            model.getSensors().forEach(s -> setupSensorSystemListeners(model, s));
        }
        if (model.getForks() != null) {
            model.getForks().forEach(f -> setupForkSystemListeners(model, f));
        }
        if (model.getStations() != null) {
            model.getStations().forEach(st -> setupStationSystemListeners(model, st));
        }
        if (model.getSemaphores() != null) {
            model.getSemaphores().forEach(sem -> setupSemaphoreSystemListeners(model, sem));
        }
    }
}
