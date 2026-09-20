package letrain.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Solar model")
class SolarModelTest {

    private static final int EQUINOX = 80;
    private static final int SUMMER = 172;
    private static final int WINTER = 355;

    @Test
    @DisplayName("equinox has ~12 h days at any latitude")
    void should_HaveTwelveHourDays_AtEquinox() {
        assertEquals(6.0, SolarModel.sunriseHour(EQUINOX, 0), 0.2);
        assertEquals(18.0, SolarModel.sunsetHour(EQUINOX, 0), 0.2);
        assertEquals(12.0, SolarModel.dayLengthHours(EQUINOX, 60), 0.3);
    }

    @Test
    @DisplayName("northern summer days are longer than winter ones")
    void should_StretchDays_InSummer() {
        assertTrue(SolarModel.dayLengthHours(SUMMER, 40) > 13.5);
        assertTrue(SolarModel.dayLengthHours(WINTER, 40) < 10.5);
    }

    @Test
    @DisplayName("polar latitudes have polar day and polar night")
    void should_HavePolarDayAndNight() {
        assertTrue(Double.isNaN(SolarModel.sunriseHour(SUMMER, 80)));
        assertEquals(24.0, SolarModel.dayLengthHours(SUMMER, 80), 1e-9);
        assertTrue(Double.isNaN(SolarModel.sunsetHour(WINTER, 80)));
        assertEquals(0.0, SolarModel.dayLengthHours(WINTER, 80), 1e-9);
    }

    @Test
    @DisplayName("noon is day and local midnight is night")
    void should_RatioDayAndNight() {
        assertEquals(0.0, SolarModel.dayNightRatio(EQUINOX, 12, 40), 1e-9);
        assertEquals(1.0, SolarModel.dayNightRatio(EQUINOX, 0, 40), 1e-9);
    }

    @Test
    @DisplayName("sunset opens a gradual twilight")
    void should_BeGradual_AtDusk() {
        double sunset = SolarModel.sunsetHour(EQUINOX, 40);

        double dusk = SolarModel.dayNightRatio(EQUINOX, sunset + 0.5, 40);

        assertTrue(dusk > 0.0 && dusk < 1.0, "ratio=" + dusk);
    }

    @Test
    @DisplayName("azimuth points east at sunrise and south at noon")
    void should_PointTheSun() {
        assertEquals(90.0, SolarModel.azimuthDegrees(EQUINOX, 6, 40), 5.0);
        assertEquals(180.0, SolarModel.azimuthDegrees(EQUINOX, 12, 40), 5.0);
    }

    @Test
    @DisplayName("polar day never reaches night and polar night never sees the sun")
    void should_HandlePolarLatitudes() {
        assertEquals(0.0, SolarModel.dayNightRatio(SUMMER, 0, 80), 1e-9);
        assertTrue(SolarModel.dayNightRatio(WINTER, 12, 80) > 0.7,
                "polar night still has some twilight at noon");
        assertEquals(1.0, SolarModel.dayNightRatio(WINTER, 12, 85), 1e-9);
    }
}
