package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Placeholders: <token> spans for the editor")
class PlaceholdersTest {

    @Test
    @DisplayName("find returns start-inclusive/end-exclusive spans in reading order")
    void findsSpans() {
        String text = "train <train> stop at station <station> speed <speed>;";

        List<int[]> spans = Placeholders.find(text);

        assertEquals(3, spans.size());
        assertEquals("<train>", text.substring(spans.get(0)[0], spans.get(0)[1]));
        assertEquals("<station>", text.substring(spans.get(1)[0], spans.get(1)[1]));
        assertEquals("<speed>", text.substring(spans.get(2)[0], spans.get(2)[1]));
        assertEquals("train", Placeholders.tokenAt(text, spans.get(0)));
        assertEquals("speed", Placeholders.tokenAt(text, spans.get(2)));
    }

    @Test
    @DisplayName("only lowercase tokens match")
    void onlyLowercase() {
        assertTrue(Placeholders.find("<Station>").isEmpty());
        assertTrue(Placeholders.find("<STATION>").isEmpty());
        assertTrue(Placeholders.find("<sta-tion>").isEmpty());
    }

    @Test
    @DisplayName("null and placeholder-free text return an empty list")
    void emptyInputs() {
        assertTrue(Placeholders.find(null).isEmpty());
        assertTrue(Placeholders.find("").isEmpty());
        assertTrue(Placeholders.find("train 1 stop;").isEmpty());
    }

    @Test
    @DisplayName("the allowed token set is the one documented for the quick reference")
    void allowedTokens() {
        for (String token : List.of("station", "sensor", "train", "fork", "semaphore", "signal",
                "speed", "limit", "count", "name", "time", "dir")) {
            assertTrue(Placeholders.isAllowedToken(token), token);
        }
        assertFalse(Placeholders.isAllowedToken("nonsense"));
        assertFalse(Placeholders.isAllowedToken(null));
    }

    @Test
    @DisplayName("well-formed but unknown lowercase tokens are still found (so callers can flag them)")
    void unknownButWellFormed() {
        List<int[]> spans = Placeholders.find("<nonsense>");

        assertEquals(1, spans.size());
        assertFalse(Placeholders.isAllowedToken(Placeholders.tokenAt("<nonsense>", spans.get(0))));
    }
}
