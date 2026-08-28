package letrain.track;

import com.badlogic.gdx.graphics.Color;

public enum CargoTypes {
    NONE(new Color(0.9f, 0.9f, 0.8f, 1f)), // Ivory/Off-white
    GOLD(new Color(1f, 0.85f, 0f, 1f)), // Bright Gold/Yellow
    COAL(new Color(0.1f, 0.1f, 0.1f, 1f)), // Shiny Black (Charcoal)
    RUBY(new Color(1f, 0f, 0.3f, 1f)); // Bright Ruby Red

    private final Color color;

    CargoTypes(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public enum StationRole {
        GENERIC, PRODUCER, CONSUMER
    }

    /** Map GroundMap value to CargoType and Role */
    public static class IndustryMapper {
        public static CargoTypes getCargoForTerrain(int terrainType) {
            switch (terrainType) {
                case 10: // GOLD_MINE
                case 20: // JEWELRY_STORE
                    return GOLD;
                case 11: // MINE
                case 21: // POWER_PLANT
                    return COAL;
                case 12: // RUBY_MINE
                case 22: // RUBY_STORE
                    return RUBY;
                default:
                    return NONE;
            }
        }

        public static StationRole getRoleForTerrain(int terrainType) {
            if (terrainType >= 10 && terrainType <= 19) {
                return StationRole.PRODUCER;
            }
            if (terrainType >= 20 && terrainType <= 29) {
                return StationRole.CONSUMER;
            }
            return StationRole.GENERIC;
        }
    }
}
