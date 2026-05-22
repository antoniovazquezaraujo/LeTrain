package letrain.mvp.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import letrain.itinerary.WaypointCommand;

import java.io.IOException;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonDeserialize(using = WaypointCommandMixin.WaypointCommandDeserializer.class)
public abstract class WaypointCommandMixin {

    public static class WaypointCommandDeserializer extends JsonDeserializer<WaypointCommand> {
        @Override
        public WaypointCommand deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();
            WaypointCommand.Kind kind = WaypointCommand.Kind.NONE;
            if (node.has("kind")) {
                kind = WaypointCommand.Kind.valueOf(node.get("kind").asText());
            }
            int seconds = 0;
            if (node.has("seconds")) {
                seconds = node.get("seconds").asInt();
            }
            int targetSpeed = 0;
            if (node.has("targetSpeed")) {
                targetSpeed = node.get("targetSpeed").asInt();
            }
            return switch (kind) {
                case LOAD -> WaypointCommand.LOAD;
                case UNLOAD -> WaypointCommand.UNLOAD;
                case REVERSE -> WaypointCommand.REVERSE;
                case WAIT -> WaypointCommand.waitSeconds(seconds);
                case SPEED -> WaypointCommand.speed(targetSpeed);
                default -> WaypointCommand.NONE;
            };
        }
    }
}
