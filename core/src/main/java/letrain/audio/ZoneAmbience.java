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
 *
 * <p>
 * The terrain is resolved from the <b>model passed to each {@link #update(Model)} call</b>, never
 * captured in the constructor (issue #805): after a New Game the world is a different
 * {@link GroundMap}, and a constructor-captured lambda would keep sampling the old map (sea heard
 * on a 0% water world). A change of ground-map identity also forces a resample even when the focus
 * has not moved, so the new world's weights replace the old ones immediately.
 */
public class ZoneAmbience {

    public record Focus(String kind, int x, int y) {
    }

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
    private Focus lastFocus;
    private Update lastUpdate;
    private long lastCompute;
    /** Ground map the last sample was taken from; a different instance forces a resample. */
    private GroundMap lastGroundMap;

    /**
     * @param radius sampling radius in cells
     * @param stride sampling stride in cells
     */
    public ZoneAmbience(int radius, int stride) {
        this.sensor = new ZoneSensor(radius, stride);
    }

    public Update update(Model model) {
        return update(model, System.nanoTime(), false);
    }

    public Update update(Model model, long nowNanos, boolean force) {
        Focus focus = resolve(model);
        if (focus == null) {
            return lastUpdate == null ? EMPTY : lastUpdate;
        }
        // Resolve the world's terrain on every sample (issue #805): never capture the GroundMap.
        GroundMap groundMap = model.getGroundMap();
        boolean mapChanged = groundMap != lastGroundMap;
        boolean kindChanged = lastFocus == null || !lastFocus.kind().equals(focus.kind());
        boolean moved =
                lastFocus == null || lastFocus.x() != focus.x() || lastFocus.y() != focus.y();
        lastFocus = focus;
        lastGroundMap = groundMap;
        boolean due = force || kindChanged || mapChanged
                || (moved && nowNanos - lastCompute >= MIN_INTERVAL_NANOS);
        if (!due && lastUpdate != null) {
            return lastUpdate;
        }
        ZoneSensor.Result result = sensor.sense(terrainAt(groundMap), focus.x(), focus.y());
        lastCompute = nowNanos;
        lastUpdate =
                new Update(result.primary(), result.weights(), result.influence(), kindChanged);
        return lastUpdate;
    }

    /** Terrain sampler bound to the given world map; an absent map reads as unknown (-1). */
    private static IntBinaryOperator terrainAt(GroundMap groundMap) {
        if (groundMap == null) {
            return (x, y) -> -1;
        }
        return (x, y) -> {
            Integer value = groundMap.getValueAt(x, y);
            return value == null ? -1 : value;
        };
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
