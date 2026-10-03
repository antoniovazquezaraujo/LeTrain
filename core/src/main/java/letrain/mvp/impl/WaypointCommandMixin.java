package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import letrain.itinerary.WaypointCommand;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonDeserialize(using = WaypointCommandMixin.WaypointCommandDeserializer.class)
public abstract class WaypointCommandMixin {

    public static class WaypointCommandDeserializer extends JsonDeserializer<WaypointCommand> {
        @Override
        public WaypointCommand deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
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
            boolean forward = node.has("forward") && node.get("forward").asBoolean();
            int count = node.has("count") ? node.get("count").asInt() : 0;
            int targetId = node.has("targetId") ? node.get("targetId").asInt() : -1;
            int forkId = node.has("forkId") ? node.get("forkId").asInt() : -1;
            String forkDirection = node.has("forkDirection") && !node.get("forkDirection").isNull()
                    ? node.get("forkDirection").asText()
                    : null;
            letrain.itinerary.TrainMission.Kind missionKind = null;
            if (node.has("missionKind") && !node.get("missionKind").isNull()) {
                missionKind = letrain.itinerary.TrainMission.Kind
                        .valueOf(node.get("missionKind").asText());
            }
            return switch (kind) {
                case LOAD -> WaypointCommand.LOAD;
                case UNLOAD -> WaypointCommand.UNLOAD;
                case REVERSE -> WaypointCommand.REVERSE;
                case STOP -> WaypointCommand.STOP;
                case PARK -> WaypointCommand.PARK;
                case WAIT -> WaypointCommand.waitSeconds(seconds);
                case SPEED -> WaypointCommand.speed(targetSpeed);
                case COUPLE -> WaypointCommand.couple(forward, count);
                case UNCOUPLE -> WaypointCommand.uncouple(forward, count);
                case MISSION -> WaypointCommand.mission(missionKind, targetId, targetSpeed);
                case FORK_SET_DIRECTION -> WaypointCommand.forkSetDirection(forkId, forkDirection);
                case FORK_FLIP -> WaypointCommand.forkFlip(forkId);
                default -> WaypointCommand.NONE;
            };
        }
    }
}
