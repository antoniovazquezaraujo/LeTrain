package letrain.mvp.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.itinerary.impl.WaypointImpl;
import letrain.map.Dir;

@JsonSerialize(using = WaypointMixin.WaypointSerializer.class)
@JsonDeserialize(using = WaypointMixin.WaypointDeserializer.class)
public abstract class WaypointMixin {

    public static class WaypointSerializer extends JsonSerializer<Waypoint> {
        @Override
        public void serialize(Waypoint value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", value.type().name());
            gen.writeNumberField("targetId", value.targetId());
            if (value.entryDir().isPresent()) {
                gen.writeStringField("entryDir", value.entryDir().get().name());
            } else {
                gen.writeNullField("entryDir");
            }
            serializers.defaultSerializeField("commands", value.commands(), gen);
            gen.writeEndObject();
        }
    }

    public static class WaypointDeserializer extends JsonDeserializer<Waypoint> {
        @Override
        public Waypoint deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Waypoint.Type type = null;
            int targetId = 0;
            Optional<Dir> entryDir = Optional.empty();
            List<WaypointCommand> commands = null;

            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();
            if (node.has("type")) {
                type = Waypoint.Type.valueOf(node.get("type").asText());
            }
            if (node.has("targetId")) {
                targetId = node.get("targetId").asInt();
            }
            if (node.has("entryDir") && !node.get("entryDir").isNull()) {
                entryDir = Optional.of(Dir.valueOf(node.get("entryDir").asText()));
            }
            if (node.has("commands")) {
                JsonParser listParser = node.get("commands").traverse(p.getCodec());
                listParser.nextToken();
                commands = ctxt.readValue(listParser, ctxt.getTypeFactory()
                        .constructCollectionType(List.class, WaypointCommand.class));
            }

            return new WaypointImpl(type, targetId, entryDir, commands);
        }
    }
}
