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
import java.time.LocalTime;
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
            writeTimeField(gen, "arrival", value.arrival());
            writeTimeField(gen, "departure", value.departure());
            if (value.entryDir().isPresent()) {
                gen.writeStringField("entryDir", value.entryDir().get().name());
            } else {
                gen.writeNullField("entryDir");
            }
            serializers.defaultSerializeField("commands", value.commands(), gen);
            gen.writeEndObject();
        }

        /** Times are written as deterministic ISO {@code HH:mm} strings (null when unscheduled). */
        private void writeTimeField(JsonGenerator gen, String field, Optional<LocalTime> time)
                throws IOException {
            if (time.isPresent()) {
                gen.writeStringField(field, time.get().toString());
            } else {
                gen.writeNullField(field);
            }
        }
    }

    public static class WaypointDeserializer extends JsonDeserializer<Waypoint> {
        @Override
        public Waypoint deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Waypoint.Type type = null;
            int targetId = 0;
            Optional<Dir> entryDir = Optional.empty();
            Optional<LocalTime> arrival = Optional.empty();
            Optional<LocalTime> departure = Optional.empty();
            List<WaypointCommand> commands = null;

            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();
            if (node.has("type")) {
                type = Waypoint.Type.valueOf(node.get("type").asText());
            }
            if (node.has("targetId")) {
                targetId = node.get("targetId").asInt();
            }
            if (node.has("arrival") && !node.get("arrival").isNull()) {
                arrival = Optional.of(LocalTime.parse(node.get("arrival").asText()));
            }
            if (node.has("departure") && !node.get("departure").isNull()) {
                departure = Optional.of(LocalTime.parse(node.get("departure").asText()));
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

            return new WaypointImpl(type, targetId, entryDir, commands, arrival, departure);
        }
    }
}
