package letrain.mvp.impl.graphic;

import java.io.IOException;
import java.time.LocalTime;
import javax.sound.sampled.LineUnavailableException;
import letrain.audio.ZoneAmbience;
import letrain.mvp.Model;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.audio.AmbientPlayer;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glue between the game and the zone ambience (ADR-025): loads the bundled style, asks the core
 * {@link ZoneAmbience} for the focus weights, composes the mix and pushes it to the player. Weather
 * stays at zero until the ADR-027 generator lands; the time of day is a placeholder until the game
 * clock arrives.
 */
public class SoundscapeAmbience implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SoundscapeAmbience.class);
    private static final String DEFAULT_STYLE = "/styles/valle-norte.sound";
    private static final int SENSOR_RADIUS = 8;
    private static final int SENSOR_STRIDE = 1;
    private static final long SEED = 1L;

    private final SoundscapeStyle style;
    private final SoundscapeEngine engine = new SoundscapeEngineImpl();
    private final ZoneAmbience zoneAmbience;
    private final AmbientPlayer player;
    private boolean playing;

    public SoundscapeAmbience(Model model) throws IOException {
        this.style = new TextStyleLoader().loadResource(DEFAULT_STYLE);
        this.zoneAmbience = new ZoneAmbience(model.getGroundMap(), SENSOR_RADIUS, SENSOR_STRIDE);
        this.player = new AmbientPlayer(style, SEED);
    }

    public void start() {
        if (playing) {
            return;
        }
        try {
            player.start();
            playing = true;
        } catch (LineUnavailableException e) {
            log.warn("no audio device, ambience stays silent: {}", e.getMessage());
        }
    }

    public void update(Model model, float height) {
        ZoneAmbience.Update update = zoneAmbience.update(model);
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, update.weights(), height, 0f, 0f, 0f);
        Composition composition = engine.compose(style, input);
        player.updateTargets(composition, update.focusChanged());
    }

    @Override
    public void close() {
        player.close();
        playing = false;
    }
}
