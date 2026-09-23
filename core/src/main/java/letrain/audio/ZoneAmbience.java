package letrain.audio;

import java.util.Map;
import java.util.function.IntBinaryOperator;
import letrain.ground.GroundMap;
import letrain.ground.ZoneSensor;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;

/**
 * Bridge between the game and the soundscape (ADR-025): resolves the listening focus from the
 * active {@link Model.GameMode}, asks the {@link ZoneSensor} for the zone weights and caps how
 * often the map is sampled. A change of focus kind is reported so the player can cut the scene.
 */
public class ZoneAmbience {

    public record Focus(String kind, int x, int y) {}

    public record Update(String primary, Map<String, Float> weights, Map<String, Float> influence,
            boolean focusChanged) {

        public Update {
            weights = Map.copyOf(weights);
            influence = Map.copyOf(influence);
        }

        public float weightOf(String zone) {
            return weights.getOrDefault(zone, 0f);
        }
    }

    private static final long MIN_INTERVAL_NANOS = 160_000_000L;
    private static final Update EMPTY = new Update(null, Map.of(), Map.of(), false);

    private final ZoneSensor sensor;
    private final IntBinaryOperator terrainAt;
    private Focus lastFocus;
    private Update lastUpdate;
    private long lastCompute;

    public ZoneAmbience(GroundMap groundMap, int radius, int stride) {
        this.sensor = new ZoneSensor(radius, stride);
        this.terrainAt = (x, y) -> {
            Integer value = groundMap.getValueAt(x, y);
            return value == null ? -1 : value;
        };
    }

    public Update update(Model model) {
        return update(model, System.nanoTime(), false);
    }

    public Update update(Model model, long nowNanos, boolean force) {
        Focus focus = resolve(model);
        if (focus == null) {
            return lastUpdate == null ? EMPTY : lastUpdate;
        }
        boolean kindChanged = lastFocus == null || !lastFocus.kind().equals(focus.kind());
        boolean moved =
                lastFocus == null || lastFocus.x() != focus.x() || lastFocus.y() != focus.y();
        lastFocus = focus;
        boolean due =
                force || kindChanged || (moved && nowNanos - lastCompute >= MIN_INTERVAL_NANOS);
        if (!due && lastUpdate != null) {
            return lastUpdate;
        }
        ZoneSensor.Result result = sensor.sense(terrainAt, focus.x(), focus.y());
        lastCompute = nowNanos;
        lastUpdate =
                new Update(result.primary(), result.weights(), result.influence(), kindChanged);
        return lastUpdate;
    }

    private Focus resolve(Model model) {
        Model.GameMode mode = model.getEffectiveMode();
        return switch (mode) {
            case DRIVE, TRAINS, LINK, UNLINK -> focus(mode, model.getSelectedLocomotive());
            case RAILS, ADD -> focus(mode, model.getCursor());
            case STATIONS, LOAD_TRAINS -> focus(mode, model.getSelectedStation());
            case SEMAPHORES -> focus(mode, model.getSelectedSemaphore());
            case SENSORS -> focus(mode, model.getSelectedSensor());
            case SPEED_SIGNALS -> focus(mode, model.getSelectedSpeedSignal());
            case FORKS -> focus(mode, model.getSelectedFork());
            default -> lastFocus;
        };
    }

    private Focus focus(Model.GameMode mode, Object entity) {
        Point position = positionOf(entity);
        if (position == null) {
            return lastFocus;
        }
        return new Focus(mode.name(), position.getX(), position.getY());
    }

    private Point positionOf(Object entity) {
        if (entity instanceof Locomotive locomotive) {
            return locomotive.getPosition();
        }
        if (entity instanceof Sensor sensorEntity) {
            return sensorEntity.getPosition();
        }
        if (entity instanceof ForkRailTrack fork) {
            return fork.getPosition();
        }
        if (entity instanceof Cursor cursor) {
            return cursor.getPosition();
        }
        return null;
    }
}
