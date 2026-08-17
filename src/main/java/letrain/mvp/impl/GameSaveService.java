package letrain.mvp.impl;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsula la lógica de persistencia de partidas para desacoplarla de la vista 3D.
 */
public class GameSaveService {

    private static final Logger log = LoggerFactory.getLogger(GameSaveService.class);

    private void configureObjectMapper(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, model);
            log.info("Game saved successfully to {} (JSON)", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Error saving game to {}", file.getAbsolutePath(), e);
            // Diagnostic logging to file for the AI agent to read
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("save_error.log"))) {
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
            letrain.mvp.impl.Model loadedModel = mapper.readValue(file, letrain.mvp.impl.Model.class);
            loadedModel.postLoadInit();
            log.info("Game loaded successfully from {} (JSON)", file.getAbsolutePath());
            return Optional.of(loadedModel);
        } catch (Exception e) {
            log.error("Error loading game from {}", file.getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    public Optional<letrain.mvp.impl.Model> load(java.io.InputStream is) {
        if (is == null) {
            log.warn("Ignoring load request with null input stream");
            return Optional.empty();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            configureObjectMapper(mapper);
            letrain.mvp.impl.Model loadedModel = mapper.readValue(is, letrain.mvp.impl.Model.class);
            loadedModel.postLoadInit();
            return Optional.of(loadedModel);
        } catch (Exception e) {
            log.error("Error loading game from input stream", e);
            e.printStackTrace();
            return Optional.empty();
        }
    }
}

