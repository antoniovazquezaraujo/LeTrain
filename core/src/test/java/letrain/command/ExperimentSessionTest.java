package letrain.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Experiment mode session (snapshot in memory + restore)")
class ExperimentSessionTest {

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

    private static byte[] serialize(Model model) {
        try {
            return newMapper().writeValueAsBytes(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Serialization is not canonical (HashMap order / @id), so compare normalized round-trips. */
    private static byte[] normalize(byte[] data) {
        try {
            letrain.mvp.impl.Model model =
                    newMapper().readValue(data, letrain.mvp.impl.Model.class);
            model.postLoadInit();
            return serialize(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ExperimentSession session() {
        return new ExperimentSession(new ExperimentSession.Codec() {
            @Override
            public byte[] toBytes(Model model) {
                return serialize(model);
            }

            @Override
            public Model fromBytes(byte[] data) {
                try {
                    letrain.mvp.impl.Model model =
                            newMapper().readValue(data, letrain.mvp.impl.Model.class);
                    model.postLoadInit();
                    return model;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static letrain.mvp.impl.Model world() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        return model;
    }

    @Test
    @DisplayName("end() restores the exact entry snapshot and deactivates")
    void end_restoresSnapshot() throws Exception {
        letrain.mvp.impl.Model live = world();
        byte[] before = serialize(live);
        ExperimentSession s = session();

        s.begin(live);
        assertTrue(s.isActive());

        // The user experiments: mutate the live world.
        live.getCursor().setPosition(new Point(7, -3));
        live.getRailMap(); // touch map
        assertFalse(java.util.Arrays.equals(before, serialize(live)));

        Model restored = s.end();
        assertNotNull(restored);
        assertFalse(s.isActive());
        assertArrayEquals(normalize(before), serialize(restored),
                "end() must restore the entry snapshot");
    }

    @Test
    @DisplayName("end() without an active session returns null and stays inactive")
    void end_withoutSession_isNull() {
        ExperimentSession s = session();
        assertNull(s.end());
        assertFalse(s.isActive());
    }

    @Test
    @DisplayName("abandon() discards the snapshot without restoring")
    void abandon_discardsSnapshot() {
        letrain.mvp.impl.Model live = world();
        ExperimentSession s = session();
        s.begin(live);
        s.abandon();
        assertFalse(s.isActive());
        assertNull(s.end(), "after abandon there is nothing to restore");
    }

    @Test
    @DisplayName("begin() while active keeps the original entry snapshot")
    void begin_whileActive_keepsFirstSnapshot() throws Exception {
        letrain.mvp.impl.Model live = world();
        byte[] before = serialize(live);
        ExperimentSession s = session();
        s.begin(live);
        live.getCursor().setPosition(new Point(3, 3));
        s.begin(live); // second call must be ignored
        Model restored = s.end();
        assertArrayEquals(normalize(before), serialize(restored));
    }
}
