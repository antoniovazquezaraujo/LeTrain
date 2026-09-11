package letrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import letrain.command.PlayerCommandExecutor;
import letrain.command.TurtleBuilder;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.track.Sensor;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PoC golden test (issue #487): prove that a representative edition script, replayed on two fresh
 * copies of the SAME base world (same serialized seed + terrain), yields byte-identical model
 * states when the turtle commands are executed by the headless {@link TurtleBuilder} (which drives
 * the very same {@link RailTrackMaker} machinery the UI console uses).
 *
 * <p>
 * The script exercises: straight construction, curves ({@code write ... r ...}), a diverging branch
 * that creates a {@link ForkRailTrack}, track elements ({@code new st/sn/sm}), moving the cursor
 * without building ({@code move}), deleting an end tile ({@code del}), and one crossing over water
 * (bridges + gates) and one through rock (tunnel + gates), located deterministically by scanning
 * {@code groundMap.getValueAt} on the natural world.
 */
@DisplayName("PoC: journal replay of turtle editing commands is deterministic")
class PocJournalReplayTest {

    // ------------------------------------------------------------------
    // Serialization helpers (same ObjectMapper + mixins as GameSaveService)
    // ------------------------------------------------------------------

    private static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.addMixIn(letrain.mvp.Model.class, letrain.mvp.impl.ModelMixin.class);
        mapper.addMixIn(letrain.mvp.impl.Model.class, letrain.mvp.impl.ModelMixin.class);
        mapper.addMixIn(letrain.vehicle.rail.impl.Train.class,
                letrain.mvp.impl.TrainMixin.class);
        mapper.addMixIn(letrain.itinerary.Waypoint.class, letrain.mvp.impl.WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.WaypointImpl.class,
                letrain.mvp.impl.WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.Itinerary.class, letrain.mvp.impl.ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.ItineraryImpl.class,
                letrain.mvp.impl.ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.AutoPilot.class, letrain.mvp.impl.AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.AutoPilotImpl.class,
                letrain.mvp.impl.AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.WaypointCommand.class,
                letrain.mvp.impl.WaypointCommandMixin.class);
        return mapper;
    }

    private static byte[] serialize(Model model) throws IOException {
        return newMapper().writeValueAsBytes(model);
    }

    private static Model deserialize(byte[] data) throws IOException {
        Model model = newMapper().readValue(data, Model.class);
        model.postLoadInit();
        return model;
    }

    // ------------------------------------------------------------------
    // Headless builder wiring (Mockito Presenter/View stubs)
    // ------------------------------------------------------------------

    /**
     * Builds a {@link RailTrackMaker} wired to {@code model} whose only view interaction is a no-op
     * mock. This is the exact same machinery the 2D/3D console {@code TurtleDelegate}s call, minus
     * the screen.
     */
    private static RailTrackMaker headlessMaker(Model model) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(model);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new RailTrackMaker(presenter);
    }

    /** Runs a full player script (the DSL used by the console) on {@code model}. */
    private static String runScript(Model model, String script) {
        TurtleBuilder builder = new TurtleBuilder(model, headlessMaker(model));
        return PlayerCommandExecutor.execute(script, model, null, null, builder);
    }

    // ------------------------------------------------------------------
    // Base world construction
    // ------------------------------------------------------------------

    private static final int HALF = 150; // rendered world spans [-HALF, HALF] on both axes

    /**
     * Fixed terrain seed. This PoC proves that replaying the same script on two copies of the same
     * base world is byte-identical; it is not a terrain-coverage test. A random seed made it flaky
     * (some worlds have no matching feature; some produce two fork nodes instead of one at the
     * branch spot), so the world is pinned for reproducibility.
     */
    private static final int SEED = 1;

    private static Model newBaseWorld() {
        Model base = new Model(SEED);
        base.updateGroundMap(new Point(-HALF, -HALF), 2 * HALF, 2 * HALF);
        // Deterministic starting cursor; every script segment repositions with `go` anyway.
        base.getCursor().setPosition(new Point(0, 0));
        base.getCursor().setDir(Dir.E);
        base.getCursor().setMode(letrain.vehicle.Cursor.CursorMode.DRAWING);
        return base;
    }

    private static int terrainValue(Model model, int x, int y) {
        Integer v = model.getGroundMap().getValueAt(x, y);
        return v == null ? -1 : v;
    }

    private static boolean isPlainGround(Model model, int x, int y) {
        return terrainValue(model, x, y) == GroundMap.GROUND;
    }

    private record Crossing(int x, int y, int run, int totalPieces) {}

    /**
     * Scans rows in {@code [minX,maxX]x[minY,maxY]} for a horizontal crossing that starts on
     * GROUND, crosses {@code run >= minRun} cells of {@code target} terrain and exits again on
     * GROUND. {@code totalPieces} is the number of {@code write} steps needed (start ground + target
     * run + exit ground).
     */
    private static Crossing findCrossing(Model model, int target, int minRun, int minX, int maxX,
            int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!isPlainGround(model, x, y)) {
                    continue;
                }
                int run = 0;
                while (terrainValue(model, x + 1 + run, y) == target
                        && x + 1 + run <= HALF - 1) {
                    run++;
                }
                if (run >= minRun && isPlainGround(model, x + 1 + run, y)) {
                    return new Crossing(x, y, run, run + 2);
                }
            }
        }
        return null;
    }

    /** First cell of a horizontal GROUND run of at least {@code length} cells. */
    private record GroundRun(int x, int y) {}

    private static GroundRun findGroundRun(Model model, int length, int minX, int maxX, int minY,
            int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x + length - 1 <= maxX; x++) {
                boolean ok = true;
                for (int i = 0; i < length; i++) {
                    if (!isPlainGround(model, x + i, y)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return new GroundRun(x, y);
                }
            }
        }
        return null;
    }

    /**
     * Finds a spot for {@code write SL, r, DL}: {@code SL} straight GROUND cells heading east, then
     * the corner cell and {@code DL-1} more cells down the SE diagonal, all on plain GROUND.
     */
    private static GroundRun findCurveSpot(Model model, int sl, int dl, int minX, int maxX,
            int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                // straight cells x..x+sl-1, corner at x+sl
                boolean ok = true;
                for (int i = 0; i <= sl; i++) {
                    if (!isPlainGround(model, x + i, y)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                for (int j = 1; j < dl; j++) {
                    if (!isPlainGround(model, x + sl + j, y + j)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return new GroundRun(x, y);
                }
            }
        }
        return null;
    }

    /** Spot for the P3 fork pattern (see {@link #buildForkScript(int, int)}). */
    private static GroundRun findForkSpot(Model model, int minX, int maxX, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                boolean ok = true;
                for (int i = -2; i <= 3; i++) {
                    if (!isPlainGround(model, x + i, y)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                if (!isPlainGround(model, x + 1, y + 1) || !isPlainGround(model, x + 2, y + 2)) {
                    continue;
                }
                return new GroundRun(x, y);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The script
    // ------------------------------------------------------------------

    private static String buildScript(Crossing water, Crossing rock, GroundRun plain,
            GroundRun curve, GroundRun fork) {
        StringBuilder sb = new StringBuilder();
        // Plain straight + elements + move along the rail.
        sb.append("go ").append(plain.x()).append(",").append(plain.y()).append("; face e; write 8; ");
        // Sensor created facing east is then slid 2 resting cells along the rail (slide command).
        sb.append("go ").append(plain.x()).append(",").append(plain.y()).append("; face e; new sn; ");
        sb.append("slide sn 1 fw 2; ");
        // Station and semaphore on clear cells not crossed by the sensor slide.
        sb.append("go ").append(plain.x() + 4).append(",").append(plain.y()).append("; face e; new st; ");
        sb.append("go ").append(plain.x() + 5).append(",").append(plain.y()).append("; face e; new sm; ");
        sb.append("go ").append(plain.x()).append(",").append(plain.y()).append("; face e; move 8; ");
        // Operator: spawn two locomotives on the plain line (cursor must face along the rail).
        sb.append("go ").append(plain.x() + 1).append(",").append(plain.y()).append("; face e; new loco A red; ");
        sb.append("go ").append(plain.x() + 3).append(",").append(plain.y()).append("; face e; new loco B blue; ");
        // Curve: 4 straight pieces then turn right and lay 3 diagonal pieces.
        sb.append("go ").append(curve.x()).append(",").append(curve.y()).append("; face e; write 4, r, 3; ");
        // Fork: a line A (fork.x..fork.x+3), a west approach to the tile fork.x, then leave SE.
        sb.append("go ").append(fork.x()).append(",").append(fork.y()).append("; face e; write 4; ");
        sb.append("go ").append(fork.x() - 2).append(",").append(fork.y()).append("; face e; write 2; ");
        sb.append("face se; write 3; ");
        // Water crossing (bridges + gates).
        sb.append("go ").append(water.x()).append(",").append(water.y()).append("; face e; write ")
                .append(water.totalPieces()).append("; ");
        // Rock crossing (tunnels + gates) then delete the very last tile.
        sb.append("go ").append(rock.x()).append(",").append(rock.y()).append("; face e; write ")
                .append(rock.totalPieces()).append("; ");
        sb.append("go ").append(rock.x() + rock.totalPieces() - 1).append(",").append(rock.y())
                .append("; face e; del 1; ");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // The golden test
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Same base world + same editing script => byte-identical serialized state")
    void replay_OnIdenticalBaseWorlds_ProducesIdenticalState() throws IOException {
        // 1. Virgin world; locate the terrain features and freeze the world as bytes.
        Model base = newBaseWorld();

        Crossing water = findCrossing(base, GroundMap.WATER, 3, -HALF + 5, -HALF / 2, -HALF + 5,
                HALF - 5);
        assertNotNull(water, "No GROUND->WATER->GROUND horizontal crossing found in the world");
        Crossing rock = findCrossing(base, GroundMap.ROCK, 2, -HALF / 2 + 1, -HALF / 4, -HALF + 5,
                HALF - 5);
        assertNotNull(rock, "No GROUND->ROCK->GROUND horizontal crossing found in the world");
        GroundRun plain = findGroundRun(base, 10, 5, HALF / 4, -HALF + 5, HALF - 5);
        assertNotNull(plain, "No plain GROUND run of 10 cells found");
        GroundRun curve = findCurveSpot(base, 4, 3, HALF / 4 + 1, HALF / 2, -HALF + 5, HALF - 5);
        assertNotNull(curve, "No plain GROUND curve spot found");
        GroundRun fork = findForkSpot(base, HALF / 2 + 1, HALF - 5, -HALF + 5, HALF - 5);
        assertNotNull(fork, "No plain GROUND fork spot found");

        byte[] baseBytes = serialize(base);

        // 2. Two fresh copies from the same bytes (same seed + same terrain).
        Model copyA = deserialize(baseBytes);
        Model copyB = deserialize(baseBytes);

        // 3. Replay the SAME representative script on each copy.
        String script = buildScript(water, rock, plain, curve, fork);
        String errorA = runScript(copyA, script);
        String errorB = runScript(copyB, script);
        assertEquals(null, errorA, "Script failed on copy A: " + errorA);
        assertEquals(null, errorB, "Script failed on copy B: " + errorB);

        // The replay must produce a rich network on both copies...
        assertRichNetwork(copyA);
        assertRichNetwork(copyB);
        // ...the plain sensor must actually have been slid 2 cells east by the slide command...
        assertSensorSlid(copyA, plain);
        assertSensorSlid(copyB, plain);

        // 4. ...and both final states must be byte-identical.
        byte[] bytesA = serialize(copyA);
        byte[] bytesB = serialize(copyB);
        assertArrayEquals(bytesA, bytesB,
                "Replay of the same script on identical base worlds diverged");
    }

    private static void assertSensorSlid(Model model, GroundRun plain) {
        assertEquals(1, model.getSensors().size(), "expected one plain sensor");
        Sensor sensor = model.getSensors().get(0);
        int restX = sensor.getTrack().getPosition().getX();
        assertEquals(plain.x() + 2, restX,
                "slide sn fw 2 should rest the sensor two cells east of the line start");
    }

    private static void assertRichNetwork(Model model) {
        assertTrue(model.getRailMap().getRails().values().stream()
                .mapToInt(m -> m.values().size()).sum() > 15, "expected a sizeable network");
        long forks = model.getForks().size();
        assertEquals(1, forks, "expected exactly one fork created by the branch segment");
        long bridgeVisual = model.getRailMap().getRails().values().stream()
                .flatMap(m -> m.values().stream())
                .filter(t -> t.getVisualType() == RailTrack.VisualType.BRIDGE
                        || t.getVisualType() == RailTrack.VisualType.BRIDGE_GATE)
                .count();
        assertTrue(bridgeVisual >= 3, "expected bridge/gate pieces over water");
        long tunnelVisual = model.getRailMap().getRails().values().stream()
                .flatMap(m -> m.values().stream())
                .filter(t -> t.getVisualType() == RailTrack.VisualType.TUNNEL
                        || t.getVisualType() == RailTrack.VisualType.TUNNEL_GATE)
                .count();
        assertTrue(tunnelVisual >= 2, "expected tunnel/gate pieces in rock");
        assertEquals(1, model.getStations().size(), "expected one station");
        assertEquals(1, model.getSensors().size(), "expected one plain sensor");
        assertEquals(1, model.getSemaphores().size(), "expected one semaphore");
        assertEquals(2, model.getLocomotives().size(), "expected two locomotives spawned");
    }
}
