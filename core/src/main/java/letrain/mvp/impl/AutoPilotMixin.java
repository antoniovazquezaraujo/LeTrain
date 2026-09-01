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
import letrain.itinerary.AutoPilot;
import letrain.itinerary.Itinerary;
import letrain.itinerary.WaypointCommand;
import letrain.itinerary.impl.AutoPilotImpl;

@JsonSerialize(using = AutoPilotMixin.AutoPilotSerializer.class)
@JsonDeserialize(using = AutoPilotMixin.AutoPilotDeserializer.class)
public abstract class AutoPilotMixin {

    public static class AutoPilotSerializer extends JsonSerializer<AutoPilot> {
        @Override
        public void serialize(AutoPilot value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            serializers.defaultSerializeField("itinerary", value.itinerary().orElse(null), gen);
            gen.writeStringField("mode", value.mode().name());
            gen.writeNumberField("currentIndex", value.currentWaypointIndex());
            if (value instanceof AutoPilotImpl impl) {
                gen.writeNumberField("waitTicks", impl.getWaitTicks());
                serializers.defaultSerializeField("pendingCommands", impl.getPendingCommands(),
                        gen);
            } else {
                gen.writeNumberField("waitTicks", 0);
                gen.writeNullField("pendingCommands");
            }
            gen.writeEndObject();
        }
    }

    public static class AutoPilotDeserializer extends JsonDeserializer<AutoPilot> {
        @Override
        public AutoPilot deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();

            Itinerary itinerary = null;
            if (node.has("itinerary") && !node.get("itinerary").isNull()) {
                JsonParser parser = node.get("itinerary").traverse(p.getCodec());
                parser.nextToken();
                itinerary = ctxt.readValue(parser, Itinerary.class);
            }

            AutoPilot.Mode mode = AutoPilot.Mode.IDLE;
            if (node.has("mode")) {
                mode = AutoPilot.Mode.valueOf(node.get("mode").asText());
            }

            int waitTicks = 0;
            if (node.has("waitTicks")) {
                waitTicks = node.get("waitTicks").asInt();
            }

            int currentIndex = 0;
            if (node.has("currentIndex")) {
                currentIndex = node.get("currentIndex").asInt();
            }

            List<WaypointCommand> pendingCommands = null;
            if (node.has("pendingCommands") && !node.get("pendingCommands").isNull()) {
                JsonParser parser = node.get("pendingCommands").traverse(p.getCodec());
                parser.nextToken();
                pendingCommands = ctxt.readValue(parser, ctxt.getTypeFactory()
                        .constructCollectionType(List.class, WaypointCommand.class));
            }

            return new AutoPilotImpl(itinerary, mode, waitTicks, pendingCommands, currentIndex);
        }
    }
}
