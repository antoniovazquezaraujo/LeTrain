package letrain.mvp.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsula la lógica de persistencia de partidas para desacoplarla de la vista 3D. */
public class GameSaveService {

    private static final Logger log = LoggerFactory.getLogger(GameSaveService.class);

    private void configureObjectMapper(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        mapper.addMixIn(letrain.mvp.Model.class, ModelMixin.class);
        mapper.addMixIn(letrain.mvp.impl.Model.class, ModelMixin.class);
        mapper.addMixIn(Train.class, TrainMixin.class);
        mapper.addMixIn(letrain.itinerary.Waypoint.class, WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.WaypointImpl.class, WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.Itinerary.class, ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.ItineraryImpl.class, ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.AutoPilot.class, AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.AutoPilotImpl.class, AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.WaypointCommand.class, WaypointCommandMixin.class);
    }

    public boolean save(letrain.mvp.Model model, File file) {
        if (file == null) {
            log.warn("Ignoring save request with null file");
            return false;
        }
        try {
            if (model.getGroundMap() instanceof letrain.ground.impl.GroundMap) {
                ((letrain.ground.impl.GroundMap) model.getGroundMap()).compactBlocks();
            }
            ObjectMapper mapper = new ObjectMapper();
            configureObjectMapper(mapper);
            mapper.disable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, model);
            log.info("Game saved successfully to {} (JSON)", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Error saving game to {}", file.getAbsolutePath(), e);
            // Diagnostic logging to file for the AI agent to read
            try (java.io.PrintWriter pw =
                    new java.io.PrintWriter(new java.io.FileWriter("save_error.log"))) {
                e.printStackTrace(pw);
                if (e.getCause() != null) {
                    pw.println("--- CAUSE ---");
                    e.getCause().printStackTrace(pw);
                }
            } catch (IOException ioe) {
                log.error("Failed to write diagnostic error log", ioe);
            }
            return false;
        }
    }

    public Optional<letrain.mvp.impl.Model> load(File file) {
        if (file == null) {
            log.warn("Ignoring load request with null file");
            return Optional.empty();
        }
        if (!file.exists()) {
            log.warn("Savegame file not found: {}", file.getAbsolutePath());
            return Optional.empty();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            configureObjectMapper(mapper);
            letrain.mvp.impl.Model loadedModel =
                    mapper.readValue(file, letrain.mvp.impl.Model.class);
            loadedModel.postLoadInit();
            log.info("Game loaded successfully from {} (JSON)", file.getAbsolutePath());
            return Optional.of(loadedModel);
        } catch (Exception e) {
            log.error("Error loading game from {}", file.getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // In-memory snapshots (ADR-020: undo/redo checkpoints + experiment mode)
    // ------------------------------------------------------------------

    /** ObjectMapper configured with the model mixins, shared by file and in-memory round-trips. */
    private ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        configureObjectMapper(mapper);
        return mapper;
    }

    /**
     * Serializes {@code model} to bytes without touching the file system. Unlike {@link #save}, it
     * does <b>not</b> compact the ground map blocks, so the live model is left unmodified (a
     * checkpoint must not mutate the world it is capturing). The bytes can be turned back into a
     * fully initialized model with {@link #fromBytes(byte[])}.
     */
    public byte[] toBytes(letrain.mvp.Model model) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            newMapper().writeValue(out, model);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error serializing model to bytes", e);
            throw new IllegalArgumentException("Could not snapshot the model in memory", e);
        }
    }

    /** Deserializes a model previously produced by {@link #toBytes(letrain.mvp.Model)}. */
    public letrain.mvp.impl.Model fromBytes(byte[] data) {
        if (data == null) {
            log.warn("Ignoring restore request with null byte array");
            return null;
        }
        try {
            letrain.mvp.impl.Model loaded =
                    newMapper().readValue(new ByteArrayInputStream(data),
                            letrain.mvp.impl.Model.class);
            loaded.postLoadInit();
            return loaded;
        } catch (Exception e) {
            log.error("Error restoring model from bytes", e);
            return null;
        }
    }

    public Optional<letrain.mvp.impl.Model> load(InputStream is) {
        if (is == null) {
            log.warn("Ignoring load request with null input stream");
            return Optional.empty();
        }
        try {
            letrain.mvp.impl.Model loadedModel = newMapper().readValue(is,
                    letrain.mvp.impl.Model.class);
            loadedModel.postLoadInit();
            return Optional.of(loadedModel);
        } catch (Exception e) {
            log.error("Error loading game from input stream", e);
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
