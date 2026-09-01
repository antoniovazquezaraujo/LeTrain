package letrain.track;



public enum CargoTypes {
    NONE("E5E5CCFF"), // Ivory/Off-white
    GOLD("FFD800FF"), // Bright Gold/Yellow
    COAL("191919FF"), // Shiny Black (Charcoal)
    RUBY("FF004CFF"); // Bright Ruby Red

    private final String color;

    CargoTypes(String color) {
        this.color = color;
    }

    public String getColor() {
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
