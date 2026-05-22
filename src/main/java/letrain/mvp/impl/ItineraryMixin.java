package letrain.mvp.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import letrain.itinerary.Itinerary;
import letrain.itinerary.Waypoint;
import letrain.itinerary.impl.ItineraryImpl;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@JsonSerialize(using = ItineraryMixin.ItinerarySerializer.class)
@JsonDeserialize(using = ItineraryMixin.ItineraryDeserializer.class)
public abstract class ItineraryMixin {

    public static class ItinerarySerializer extends JsonSerializer<Itinerary> {
        @Override
        public void serialize(Itinerary value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            serializers.defaultSerializeField("waypoints", value.waypoints(), gen);
            serializers.defaultSerializeField("assignedTrains", value.assignedTrains(), gen);
            gen.writeStringField("state", value.state().name());
            gen.writeNumberField("currentIndex", value.currentIndex());
            gen.writeEndObject();
        }
    }

    public static class ItineraryDeserializer extends JsonDeserializer<Itinerary> {
        @Override
        public Itinerary deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();

            List<Waypoint> waypoints = null;
            if (node.has("waypoints")) {
                JsonParser parser = node.get("waypoints").traverse(p.getCodec());
                parser.nextToken();
                waypoints = ctxt.readValue(parser, ctxt.getTypeFactory().constructCollectionType(List.class, Waypoint.class));
            }

            Set<Integer> assignedTrains = null;
            if (node.has("assignedTrains")) {
                JsonParser parser = node.get("assignedTrains").traverse(p.getCodec());
                parser.nextToken();
                assignedTrains = ctxt.readValue(parser, ctxt.getTypeFactory().constructCollectionType(Set.class, Integer.class));
            }

            Itinerary.State state = Itinerary.State.CREATED;
            if (node.has("state")) {
                state = Itinerary.State.valueOf(node.get("state").asText());
            }

            int currentIndex = 0;
            if (node.has("currentIndex")) {
                currentIndex = node.get("currentIndex").asInt();
            }

            return new ItineraryImpl(waypoints, assignedTrains, state, currentIndex);
        }
    }
}
