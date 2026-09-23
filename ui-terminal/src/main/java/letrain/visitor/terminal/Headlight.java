package letrain.visitor.terminal;

import letrain.map.Dir;

/**
 * Cono de luz de los faros de locomotora para el cliente 2D (ADR-022 fase 1e parcial). Pura
 * geometría: dada la posición de una celda respecto a la locomotora y su dirección, devuelve cuánto
 * la ilumina el faro (0 = nada, 1 = justo delante). El renderizado mezcla con ese factor los
 * colores nocturnos de la paleta con los diurnos, así que el haz "aclara" la vía y el terreno.
 */
public final class Headlight {

    /** Alcance del faro, en celdas: ilumina un par de ellas por delante y se apaga. */
    public static final int RADIUS = 3;

    /**
     * Cuánto puede acercar el faro una celda a la paleta diurna como máximo. Menos de 1 para que la
     * noche siga notándose dentro del haz (no es un amanecer, es un faro).
     */
    public static final float MAX_LIGHT = 0.75f;

    /** Semiángulo del haz: coseno del ángulo máximo respecto a la dirección de la locomotora. */
    private static final float MIN_DOT = (float) Math.cos(Math.toRadians(40));

    private Headlight() {}

    /**
     * Iluminación de la celda a {@code (dx, dy)} celdas de la locomotora, que mira hacia
     * {@code (dirX, dirY)} (normalizado). Devuelve 0 si queda fuera del cono o del alcance.
     */
    public static float factor(int dx, int dy, float dirX, float dirY) {
        if (dx == 0 && dy == 0) {
            return 1f;
        }
        float distance = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
        if (distance > RADIUS) {
            return 0f;
        }
        float dot = (dx * dirX + dy * dirY) / distance;
        if (dot <= MIN_DOT) {
            return 0f;
        }
        float distanceFalloff = 1f - distance / RADIUS;
        float angleFalloff = (dot - MIN_DOT) / (1f - MIN_DOT);
        return distanceFalloff * angleFalloff;
    }

    /**
     * Igual que {@link #factor(int, int, float, float)} pero tomando la dirección de mapa tal cual
     * llega de los vehículos (N es Y-, E es X+).
     */
    public static float factor(int dx, int dy, Dir dir) {
        if (dir == null) {
            return 0f;
        }
        int vx = 0;
        int vy = 0;
        switch (dir) {
            case E:
                vx = 1;
                break;
            case NE:
                vx = 1;
                vy = -1;
                break;
            case N:
                vy = -1;
                break;
            case NW:
                vx = -1;
                vy = -1;
                break;
            case W:
                vx = -1;
                break;
            case SW:
                vx = -1;
                vy = 1;
                break;
            case S:
                vy = 1;
                break;
            case SE:
                vx = 1;
                vy = 1;
                break;
        }
        float length = (float) Math.sqrt((double) vx * vx + (double) vy * vy);
        return factor(dx, dy, vx / length, vy / length);
    }
}
