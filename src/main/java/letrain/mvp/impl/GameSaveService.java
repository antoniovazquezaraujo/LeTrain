package letrain.mvp.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;

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
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(model);
            log.info("Game saved successfully to {}", file.getAbsolutePath());
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

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            letrain.mvp.impl.Model loadedModel = (letrain.mvp.impl.Model) ois.readObject();
            log.info("Game loaded successfully from {}", file.getAbsolutePath());
            return Optional.of(loadedModel);
        } catch (IOException | ClassNotFoundException e) {
            log.error("Error loading game from {}", file.getAbsolutePath(), e);
            return Optional.empty();
        }
    }
}

