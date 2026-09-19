package letrain.mvp.impl.graphic;

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
import letrain.time.GameClock;
import letrain.time.GameClockListener;
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
    private final WeatherGenerator weather = new WeatherGenerator(SEED);
    private WeatherState weatherState = WeatherState.CLEAR;
    private boolean playing;

    public SoundscapeAmbience(Model model) throws IOException {
        this.style = new TextStyleLoader().loadResource(DEFAULT_STYLE);
        this.zoneAmbience = new ZoneAmbience(model.getGroundMap(), SENSOR_RADIUS, SENSOR_STRIDE);
        this.player = new AmbientPlayer(style, SEED);
        this.calendar = calendarOf(style);
        GameClock clock = model.getGameClock();
        clock.addListener(new GameClockListener() {
            @Override
            public void onHourChanged(GameTime time) {
                weatherState = weather.update(calendar, dayOfYear(time), time.hour());
            }
        });
        this.weatherState = weather.update(calendar, dayOfYear(clock.now()), clock.now().hour());
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
        CompositionInput input =
                new CompositionInput(LocalTime.of(now.hour(), now.minute()), update.weights(),
                        height, weatherState.rain(), weatherState.wind(), weatherState.storm());
        Composition composition = engine.compose(style, input);
        player.updateTargets(composition, update.focusChanged());
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
