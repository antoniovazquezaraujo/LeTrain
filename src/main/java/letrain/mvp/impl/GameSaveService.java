package letrain.mvp.impl;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsula la lógica de persistencia de partidas para desacoplarla de la vista 3D.
 */
public class GameSaveService {

    private static final Logger log = LoggerFactory.getLogger(GameSaveService.class);

    public boolean save(letrain.mvp.Model model, File file) {
        if (file == null) {
            log.warn("Ignoring save request with null file");
            return false;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, model);
            log.info("Game saved successfully to {} (JSON)", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("Error saving game to {}", file.getAbsolutePath(), e);
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
            mapper.registerModule(new JavaTimeModule());
            letrain.mvp.impl.Model loadedModel = mapper.readValue(file, letrain.mvp.impl.Model.class);
            loadedModel.postLoadInit();
            log.info("Game loaded successfully from {} (JSON)", file.getAbsolutePath());
            return Optional.of(loadedModel);
        } catch (IOException e) {
            log.error("Error loading game from {}", file.getAbsolutePath(), e);
            return Optional.empty();
        }
    }
}

