package letrain.track;

import com.badlogic.gdx.graphics.Color;

public enum CargoTypes {
    NONE(new Color(0.9f, 0.9f, 0.8f, 1f)), // Ivory/Off-white
    WOOD(new Color(0.2f, 0.5f, 0.2f, 1f)), // Natural Forest Green
    COAL(new Color(0.25f, 0.25f, 0.25f, 1f)), // Charcoal
    FISH(new Color(0.3f, 0.5f, 0.9f, 1f)); // Deep Sky Blue

    private final Color color;

    CargoTypes(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public enum StationRole {
        GENERIC,
        PRODUCER,
        CONSUMER
    }

    /**
     * Map GroundMap value to CargoType and Role
     */
    public static class IndustryMapper {
        public static CargoTypes getCargoForTerrain(int terrainType) {
            switch (terrainType) {
                case 10: // FOREST
                case 20: // SAWMILL
                    return WOOD;
                case 11: // MINE
                case 21: // POWER_PLANT
                    return COAL;
                case 12: // PORT
                case 22: // MARKET
                    return FISH;
                default:
                    return NONE;
            }
        }

        public static StationRole getRoleForTerrain(int terrainType) {
            if (terrainType >= 10 && terrainType <= 19)
                return StationRole.PRODUCER;
            if (terrainType >= 20 && terrainType <= 29)
                return StationRole.CONSUMER;
            return StationRole.GENERIC;
        }
    }
}
