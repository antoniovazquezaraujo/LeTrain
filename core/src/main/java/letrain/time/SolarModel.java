package letrain.time;

/**
 * Simplified solar geometry for the game clock (ADR-022 phase 1): deterministic sunrise/sunset and
 * sun position for a day of year, hour and latitude. The hour is local solar time (no equation of
 * time, no longitude/timezone): the game clock is the sun's clock.
 *
 * <p>
 * The day/night ratio is derived from the sun elevation with a twilight band of
 * {@link #TWILIGHT_ELEVATION} degrees: 0 from sunrise on, 1 once the sun is that far below the
 * horizon. At high latitudes this yields long twilights in summer and polar day/night at the
 * extremes, with no special cases.
 */
public final class SolarModel {

    /** Default world latitude (mid-northern): ~1.5 h of twilight at the equinox. */
    public static final double DEFAULT_LATITUDE = 40.0;

    /** Sun elevation (degrees) below the horizon at which the night is complete. */
    public static final double TWILIGHT_ELEVATION = 18.0;

    private static final double DEG = Math.PI / 180.0;
    private static final double AXIAL_TILT = 23.45;

    private SolarModel() {}

    /** 1-based day of year for a 1-based game day (the calendar has 366 days). */
    public static int dayOfYear(int day) {
        return ((day - 1) % 366) + 1;
    }

    /** Solar declination (degrees) for a 1-based day of year (Cooper's equation). */
    public static double declinationDegrees(int dayOfYear) {
        return AXIAL_TILT * Math.sin(2.0 * Math.PI * (284 + dayOfYear) / 365.0);
    }

    /** Sun elevation (degrees) above the horizon; negative at night. */
    public static double elevationDegrees(int dayOfYear, double hour, double latitude) {
        double declination = declinationDegrees(dayOfYear) * DEG;
        double lat = clampLatitude(latitude) * DEG;
        double hourAngle = 15.0 * (hour - 12.0) * DEG;
        double sinElevation = Math.sin(lat) * Math.sin(declination)
                + Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle);
        return Math.asin(Math.max(-1.0, Math.min(1.0, sinElevation))) / DEG;
    }

    /** Sun azimuth (degrees clockwise from north: 90 = east, 180 = south, 270 = west). */
    public static double azimuthDegrees(int dayOfYear, double hour, double latitude) {
        double declination = declinationDegrees(dayOfYear) * DEG;
        double lat = clampLatitude(latitude) * DEG;
        double hourAngle = 15.0 * (hour - 12.0) * DEG;
        double x = Math.cos(lat) * Math.tan(declination) - Math.sin(lat) * Math.cos(hourAngle);
        double y = -Math.sin(hourAngle);
        return (Math.atan2(y, x) / DEG + 360.0) % 360.0;
    }

    /**
     * 0.0 from sunrise on, 1.0 once the sun is {@link #TWILIGHT_ELEVATION} degrees below the
     * horizon; linear on elevation in between.
     */
    public static double dayNightRatio(int dayOfYear, double hour, double latitude) {
        double elevation = elevationDegrees(dayOfYear, hour, latitude);
        if (elevation >= 0.0) {
            return 0.0;
        }
        if (elevation <= -TWILIGHT_ELEVATION) {
            return 1.0;
        }
        return -elevation / TWILIGHT_ELEVATION;
    }

    /**
     * Sunrise hour (solar time), or {@link Double#NaN} when the sun does not rise (polar night or
     * polar day).
     */
    public static double sunriseHour(int dayOfYear, double latitude) {
        double cosHourAngle = cosSunsetHourAngle(dayOfYear, latitude);
        if (!hasSunEvents(cosHourAngle)) {
            return Double.NaN;
        }
        return 12.0 - Math.acos(cosHourAngle) / DEG / 15.0;
    }

    /**
     * Sunset hour (solar time), or {@link Double#NaN} when the sun does not set (polar night or
     * polar day).
     */
    public static double sunsetHour(int dayOfYear, double latitude) {
        double cosHourAngle = cosSunsetHourAngle(dayOfYear, latitude);
        if (!hasSunEvents(cosHourAngle)) {
            return Double.NaN;
        }
        return 12.0 + Math.acos(cosHourAngle) / DEG / 15.0;
    }

    private static boolean hasSunEvents(double cosHourAngle) {
        return !Double.isNaN(cosHourAngle) && Math.abs(cosHourAngle) < 1.0;
    }

    /** Day length in hours; 0 in polar night, 24 in polar day. */
    public static double dayLengthHours(int dayOfYear, double latitude) {
        double cosHourAngle = cosSunsetHourAngle(dayOfYear, latitude);
        if (Double.isNaN(cosHourAngle)) {
            return 0.0;
        }
        if (cosHourAngle >= 1.0) {
            return 0.0;
        }
        if (cosHourAngle <= -1.0) {
            return 24.0;
        }
        return 2.0 * Math.acos(cosHourAngle) / DEG / 15.0;
    }

    private static double cosSunsetHourAngle(int dayOfYear, double latitude) {
        double declination = declinationDegrees(dayOfYear) * DEG;
        double lat = clampLatitude(latitude) * DEG;
        return -Math.tan(lat) * Math.tan(declination);
    }

    private static double clampLatitude(double latitude) {
        return Math.max(-90.0, Math.min(90.0, latitude));
    }
}
