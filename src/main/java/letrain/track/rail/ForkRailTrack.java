package letrain.track.rail;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonIgnore;

import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.impl.ForkRouter;
import letrain.track.ForkEventListener;
import letrain.utils.Pair;
import letrain.utils.SerializationHelper;
import letrain.visitor.Visitor;

public class ForkRailTrack extends RailTrack implements DynamicRouter {

    int id;
    private boolean locked = false;
    private letrain.map.Dir creationDir = letrain.map.Dir.E;
    private transient List<ForkEventListener> listeners = new ArrayList<>();
    private transient List<ForkEventListener> systemListeners = new ArrayList<>();

    public void addForkEventListener(ForkEventListener listener) {
        if (listeners == null)
            listeners = new ArrayList<>();
        listeners.add(listener);
    }

    public void addSystemForkEventListener(ForkEventListener listener) {
        if (systemListeners == null)
            systemListeners = new ArrayList<>();
        systemListeners.add(listener);
    }

    public void removeForkEventListener(ForkEventListener listener) {
        if (listeners != null)
            listeners.remove(listener);
    }

    public void removeAllForkEventListeners() {
        if (listeners != null)
            listeners.clear();
    }

    public ForkRailTrack(int id) {
        setId(id);
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected ForkRailTrack() {
    }

    /**
     * Reinitializes transient fields after deserialization.
     * Ensures listener collections are not null to prevent NPE.
     */
    private void readObject(ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.listeners = SerializationHelper.ensureListInitialized(listeners);
        this.systemListeners = SerializationHelper.ensureListInitialized(systemListeners);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train) {
        onEnterTrain(train, calculateIsForward(train));
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        if (listeners != null) {
            for (ForkEventListener listener : listeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (ForkEventListener listener : systemListeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train) {
        onExitTrain(train, calculateIsForward(train));
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        if (listeners != null) {
            for (ForkEventListener listener : listeners) {
                listener.onExitTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (ForkEventListener listener : systemListeners) {
                listener.onExitTrain(train, isForward);
            }
        }
    }

    private boolean calculateIsForward(letrain.vehicle.impl.rail.Train train) {
        if (train.getDirectorLinker() == null) {
            return true;
        }
        letrain.map.Dir realDir = train.getDirectorLinker().getRealDir();
        if (getOriginalRoute() == null) {
            return true;
        }
        letrain.map.Dir root = getOriginalRoute().getKey();
        // Forward is divergent (moving AWAY from root)
        return realDir != root;
    }

    public letrain.map.Dir getCreationDir() {
        return creationDir;
    }

    public void setCreationDir(letrain.map.Dir creationDir) {
        this.creationDir = creationDir;
    }

    @Override
    public DynamicRouter getRouter() {
        if (router == null) {
            router = new ForkRouter();
        }
        return (DynamicRouter) router;
    }

    /***********************************************************
     * Router implementation
     **********************************************************/

    @Override
    @JsonIgnore
    public int getNumRoutes() {
        return 3;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitForkRailTrack(this);
    }

    @Override
    @JsonIgnore
    public void setAlternativeRoute() {
        boolean changed = !getRouter().isUsingAlternativeRoute();
        getRouter().setAlternativeRoute();
        if (changed) {
            if (listeners != null) {
                for (ForkEventListener listener : listeners) {
                    listener.onDirectionChanged(false);
                }
            }
            if (systemListeners != null) {
                for (ForkEventListener listener : systemListeners) {
                    listener.onDirectionChanged(false);
                }
            }
        }
    }

    @Override
    @JsonIgnore
    public void setNormalRoute() {
        boolean changed = getRouter().isUsingAlternativeRoute();
        getRouter().setNormalRoute();
        if (changed) {
            if (listeners != null) {
                for (ForkEventListener listener : listeners) {
                    listener.onDirectionChanged(true);
                }
            }
            if (systemListeners != null) {
                for (ForkEventListener listener : systemListeners) {
                    listener.onDirectionChanged(true);
                }
            }
        }
    }

    @Override
    public boolean flipRoute() {
        // ADR-005 Mandamiento 6: Bloqueo de Agujas
        // 1. Bloqueo Físico: Prohibido si hay cualquier vehículo encima.
        if (getLinker() != null) {
            return false;
        }

        // 2. Bloqueo Lógico (Anclaje): Prohibido si algún tren tiene bloqueado un segmento que depende de este Fork.
        // Necesitamos acceso al BlockManager para esto. 
        // Como no queremos pasar el Model a cada RailTrack, lo gestionamos a través de los systemListeners
        // o permitiendo que el llamador lo verifique.
        // Sin embargo, para cumplir el ADR, el Fork debe ser capaz de denegar el cambio.
        if (isLocked()) {
            return false;
        }

        boolean ret = getRouter().flipRoute();
        if (listeners != null) {
            for (ForkEventListener listener : listeners) {
                listener.onDirectionChanged(!getRouter().isUsingAlternativeRoute());
            }
        }
        if (systemListeners != null) {
            for (ForkEventListener listener : systemListeners) {
                listener.onDirectionChanged(!getRouter().isUsingAlternativeRoute());
            }
        }
        return ret;
    }

    @Override
    @JsonIgnore
    public boolean isUsingAlternativeRoute() {
        return getRouter().isUsingAlternativeRoute();
    }

    @Override
    @JsonIgnore
    public Pair<Dir, Dir> getAlternativeRoute() {
        return getRouter().getAlternativeRoute();
    }

    @Override
    @JsonIgnore
    public Pair<Dir, Dir> getOriginalRoute() {
        return getRouter().getOriginalRoute();
    }

    @Override
    @JsonIgnore
    public Dir getAnyDir() {
        return getRouter().getAnyDir();
    }

    @Override
    @JsonIgnore
    public boolean isStraight() {
        return getRouter().isStraight();
    }

    @Override
    @JsonIgnore
    public boolean isCurve() {
        return getRouter().isCurve();
    }

    @Override
    @JsonIgnore
    public boolean isCross() {
        return getRouter().isCross();
    }

    @Override
    public Dir getDir(Dir dir) {
        return getRouter().getDir(dir);
    }

    @Override
    @JsonIgnore
    public Dir getFirstOpenDir() {
        return getRouter().getFirstOpenDir();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        getRouter().addRoute(from, to);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        getRouter().removeRoute(from, to);
    }

    @Override
    public void clear() {
        getRouter().clear();
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        getRouter().forEach(routeConsumer);
    }
}
