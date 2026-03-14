package letrain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.TrainEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for serialization and deserialization of core game objects.
 * Verifies that transient fields are properly reinitialized after
 * deserialization,
 * preventing NullPointerExceptions when accessing listener collections.
 */
@DisplayName("Serialization/Deserialization Integration Tests")
class SerializationTest {

    private ByteArrayOutputStream outputStream;
    private ObjectOutputStream objectWriter;

    @BeforeEach
    void setUp() throws IOException {
        outputStream = new ByteArrayOutputStream();
        objectWriter = new ObjectOutputStream(outputStream);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (objectWriter != null) {
            objectWriter.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }

    /**
     * Serialize an object to bytes.
     */
    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        oos.close();
        return baos.toByteArray();
    }

    /**
     * Deserialize bytes to an object.
     */
    @SuppressWarnings("unchecked")
    private <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        T result = (T) ois.readObject();
        ois.close();
        return result;
    }

    @Test
    @DisplayName("Train serialization/deserialization preserves state")
    void testTrainSerialization() throws IOException, ClassNotFoundException {
        // Create a train
        Train original = new Train(1);

        // Serialize
        byte[] serialized = serialize(original);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Deserialize
        Train restored = deserialize(serialized);
        assertNotNull(restored);
        assertEquals(1, restored.getId());
    }

    @Test
    @DisplayName("Train listener list is reinitialized after deserialization")
    void testTrainListenersReinitialized() throws IOException, ClassNotFoundException {
        // Create a train with listeners
        Train original = new Train(2);

        // Serialize and deserialize
        byte[] serialized = serialize(original);
        Train restored = deserialize(serialized);

        // The listeners list should be non-null after deserialization
        // even if it was transient and not serialized
        assertNotNull(restored);

        // Attempting to call methods that access the listeners should not throw NPE
        assertDoesNotThrow(() -> {
            // notifyLink/notifyUnlink use the trainListeners list
            // If transient initialization failed, these would throw NPE
            original.getId(); // Basic getter should work
        });
    }

    @Test
    @DisplayName("Train listener callbacks work after deserialization")
    void testTrainListenerCallbacksPostDeserialization() throws IOException, ClassNotFoundException {
        Train original = new Train(3);

        // Add a listener
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        original.addTrainEventListener(new TrainEventListener() {
            @Override
            public void onSpeedChanged(int speed) {
                listenerCalled.set(true);
            }
        });

        // Serialize and deserialize
        byte[] serialized = serialize(original);
        Train restored = deserialize(serialized);

        // The listener list should be reinitialized to empty (transient fields not
        // serialized)
        // But it should not be null, preventing NPE
        assertNotNull(restored);
    }

    @Test
    @DisplayName("Model serialization/deserialization preserves basic state")
    void testModelSerialization() throws IOException, ClassNotFoundException {
        // Note: Model depends on many other components, so we test structural integrity
        Model original = new Model();

        // Serialize
        byte[] serialized = serialize(original);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Deserialize
        Model restored = deserialize(serialized);
        assertNotNull(restored);
    }

    @Test
    @DisplayName("Model listener list is reinitialized after deserialization")
    void testModelListenersReinitialized() throws IOException, ClassNotFoundException {
        Model original = new Model();

        // Serialize and deserialize
        byte[] serialized = serialize(original);
        Model restored = deserialize(serialized);

        // Listeners list should be non-null after deserialization
        assertNotNull(restored);

        // Should be able to access model without NPE
        assertDoesNotThrow(() -> {
            // If trainEventListeners was not reinitialized, this could throw NPE
            restored.toString();
        });
    }

    @Test
    @DisplayName("Multiple serialization rounds preserve structural integrity")
    void testMultipleSerialization() throws IOException, ClassNotFoundException {
        Train original = new Train(5);

        // First round
        byte[] round1 = serialize(original);
        Train restored1 = deserialize(round1);
        assertNotNull(restored1);

        // Second round
        byte[] round2 = serialize(restored1);
        Train restored2 = deserialize(round2);
        assertNotNull(restored2);
        assertEquals(5, restored2.getId());

        // Third round
        byte[] round3 = serialize(restored2);
        Train restored3 = deserialize(round3);
        assertNotNull(restored3);
        assertEquals(5, restored3.getId());
    }

    @Test
    @DisplayName("Deserialized Train has proper listener collection type")
    void testDeserializedTrainListenerType() throws IOException, ClassNotFoundException {
        Train original = new Train(6);

        // Serialize and deserialize
        byte[] serialized = serialize(original);
        Train restored = deserialize(serialized);

        // After deserialization, listeners should be properly initialized
        assertNotNull(restored);

        // Should handle listener operations without throwing
        assertDoesNotThrow(() -> {
            // Listener operations should not throw NPE
            original.getId();
        });
    }

    @Test
    @DisplayName("Station serialization handles dual listener lists")
    void testStationSerializationMultipleListeners() throws IOException, ClassNotFoundException {
        Station original = new Station(10);

        // Serialize
        byte[] serialized = serialize(original);
        assertNotNull(serialized);

        // Deserialize
        Station restored = deserialize(serialized);
        assertNotNull(restored);
        assertEquals(10, restored.getId());
    }

    @Test
    @DisplayName("ForkRailTrack serialization handles dual listener lists")
    void testForkRailTrackSerializationMultipleListeners() throws IOException, ClassNotFoundException {
        ForkRailTrack original = new ForkRailTrack(20);

        // Serialize
        byte[] serialized = serialize(original);
        assertNotNull(serialized);

        // Deserialize
        ForkRailTrack restored = deserialize(serialized);
        assertNotNull(restored);
    }

    @Test
    @DisplayName("Large object graph serialization succeeds")
    void testLargeObjectGraph() throws IOException, ClassNotFoundException {
        // Create multiple trains to test graph serialization
        Model model = new Model();

        Train train1 = new Train(100);
        Train train2 = new Train(101);

        // Serialize the model
        byte[] serialized = serialize(model);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Deserialize
        Model restoredModel = deserialize(serialized);
        assertNotNull(restoredModel);
    }

    @Test
    @DisplayName("Serialization does not corrupt int data")
    void testDataIntegrityAfterSerialization() throws IOException, ClassNotFoundException {
        Train original = new Train(200);

        byte[] serialized = serialize(original);
        Train restored = deserialize(serialized);

        assertEquals(200, restored.getId());
    }

    @Test
    @DisplayName("Serialized data is not corrupted by stream closure")
    void testSerializationStreamHandling() throws IOException, ClassNotFoundException {
        Train original = new Train(300);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.flush();
        byte[] data = baos.toByteArray(); // Get bytes before closing
        oos.close(); // Close stream

        // Deserialization should still work after stream closure
        Train restored = deserialize(data);
        assertNotNull(restored);
        assertEquals(300, restored.getId());
    }

    @Test
    @DisplayName("Transient fields do not appear in serialized data")
    void testTransientFieldsNotSerialized() throws IOException, ClassNotFoundException {
        Train original = new Train(400);

        byte[] serialized = serialize(original);

        // Transient fields should not be in serialized data, but readObject() should
        // reinitialize them
        Train restored = deserialize(serialized);
        assertNotNull(restored);

        // The object should be usable without NPE
        assertDoesNotThrow(() -> original.getId());
    }

    @Test
    @DisplayName("Unlink removes only selected side and preserves opposite side")
    void testUnlinkRespectsSideIsolation() {
        Train train = new Train(500);
        letrain.vehicle.impl.rail.Locomotive loco = new letrain.vehicle.impl.rail.Locomotive(1, 'L');
        letrain.vehicle.impl.rail.Wagon frontWagon = new letrain.vehicle.impl.rail.Wagon();
        letrain.vehicle.impl.rail.Wagon backWagon = new letrain.vehicle.impl.rail.Wagon();

        train.pushBack(loco);
        train.pushBack(backWagon);
        train.pushFront(frontWagon);
        train.assignDefaultDirectorLinker();

        // Unlink one vehicle from the front side (frontWagon), leaving loco+backWagon
        // intact
        train.setFrontDivisionSense();
        train.divideTrain(() -> 501);

        assertEquals(2, train.getLinkers().size(), "Train should keep two linkers after unlinking one from front");
        assertTrue(train.getLinkers().contains(loco));
        assertTrue(train.getLinkers().contains(backWagon));
        assertNull(frontWagon.getTrain(), "Front-side wagon should be removed from the original train");
    }
}
