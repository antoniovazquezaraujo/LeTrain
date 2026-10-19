package letrain.game.audio;

import java.io.IOException;
import java.time.LocalTime;
import javax.sound.sampled.LineUnavailableException;
import letrain.audio.ZoneAmbience;
import letrain.mvp.Model;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SeasonRange;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.audio.AmbientPlayer;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;
import letrain.time.GameTime;
import letrain.weather.ClimateCalendar;
import letrain.weather.WeatherGenerator;
import letrain.weather.WeatherProbabilities;
import letrain.weather.WeatherState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glue between the game and the zone ambience (ADR-025 + ADR-027): loads the bundled style, asks
 * the core {@link ZoneAmbience} for the focus weights, generates the weather from the style
 * calendar and the game clock, composes the mix and pushes it to the player.
 *
 * <p>
 * The ambience instance is built once by the launchers and reused across games, so it must never
 * capture world state: the terrain comes from the model passed to {@link #update(Model, float)}
 * (via {@link ZoneAmbience}) and the weather is re-derived from that same model's clock (issue
 * #805). This is what makes a New Game with a fresh {@code GroundMap}/clock sound correct without
 * rebuilding the audio glue.
 */
public class SoundscapeAmbience implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SoundscapeAmbience.class);
    private static final String DEFAULT_STYLE = "/styles/valle-norte.sound";
    private static final int SENSOR_RADIUS = 8;
    private static final int SENSOR_STRIDE = 1;
    private static final long SEED = 1L;
    private static final int DAYS_PER_YEAR = 366;

    private final SoundscapeStyle style;
    private final SoundscapeEngine engine = new SoundscapeEngineImpl();
    private final ZoneAmbience zoneAmbience;
    private final AmbientPlayer player;
    private final ClimateCalendar calendar;
    private WeatherGenerator weather = new WeatherGenerator(SEED);
    private WeatherState weatherState = WeatherState.CLEAR;
    /** Model whose clock currently drives the weather (detects New Game/model swaps). */
    private Model weatherModel;
    private int weatherDay = -1;
    private int weatherHour = -1;
    private boolean playing;

    /**
     * @param model boot model only; it is deliberately not retained. Terrain and weather are
     *        resolved from the model passed to each {@link #update(Model, float)} so a New Game
     *        keeps working with the same ambience instance (issue #805).
     */
    public SoundscapeAmbience(Model model) throws IOException {
        this.style = new TextStyleLoader().loadResource(DEFAULT_STYLE);
        this.zoneAmbience = new ZoneAmbience(SENSOR_RADIUS, SENSOR_STRIDE);
        this.player = new AmbientPlayer(style, SEED);
        this.calendar = calendarOf(style);
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
        GameTime now = model.getGameClock().now();
        refreshWeather(model, now);
        CompositionInput input =
                new CompositionInput(LocalTime.of(now.hour(), now.minute()), update.weights(),
                        height, weatherState.rain(), weatherState.wind(), weatherState.storm());
        Composition composition = engine.compose(style, input);
        player.updateTargets(composition, update.focusChanged());
    }

    /**
     * Derives the hourly weather from the <b>current</b> model's clock (issue #805). We
     * deliberately do not register a {@code GameClockListener}: the ambience is built once by the
     * launchers with the boot model, so a listener would stay bound to the old clock after a New
     * Game and keep the old model alive. Advancing only when the (day, hour) pair changes keeps one
     * weather step per game hour, as the previous listener did.
     *
     * <p>
     * A model swap (New Game / load) resets the generator, so weather from the previous world never
     * leaks into the new one. The generator is deterministic (fixed seed), so the new world starts
     * the same reproducible sequence as a fresh boot.
     */
    private void refreshWeather(Model model, GameTime now) {
        int day = dayOfYear(now);
        int hour = now.hour();
        if (model != weatherModel) {
            weather = new WeatherGenerator(SEED);
            weatherModel = model;
            weatherDay = -1;
            weatherHour = -1;
        }
        if (day != weatherDay || hour != weatherHour) {
            weatherState = weather.update(calendar, day, hour);
            weatherDay = day;
            weatherHour = hour;
        }
    }

    @Override
    public void close() {
        player.close();
        playing = false;
    }

    private ClimateCalendar calendarOf(SoundscapeStyle source) {
        java.util.List<letrain.weather.SeasonRange> ranges = new java.util.ArrayList<>();
        for (SeasonRange range : source.seasons()) {
            ranges.add(new letrain.weather.SeasonRange(range.startDay(), range.endDay(),
                    WeatherProbabilities.from(range.probabilities())));
        }
        return new ClimateCalendar(ranges, WeatherProbabilities.from(source.seasonDefaults()));
    }

    private int dayOfYear(GameTime time) {
        return ((time.day() - 1) % DAYS_PER_YEAR) + 1;
    }
}
