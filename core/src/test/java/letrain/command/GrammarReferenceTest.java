package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GrammarReference: per-tab command groups")
class GrammarReferenceTest {

    private static List<String> snippets(GrammarReference.Group group) {
        List<String> out = new ArrayList<>();
        collect(GrammarReference.getReferenceTree(group), out);
        return out;
    }

    private static void collect(List<GrammarReference.Node> nodes, List<String> out) {
        for (GrammarReference.Node node : nodes) {
            if (node.snippet != null) {
                out.add(node.snippet);
            }
            collect(node.children, out);
        }
    }

    private static boolean anyContains(List<String> snippets, String needle) {
        return snippets.stream().anyMatch(s -> s.contains(needle));
    }

    @Test
    @DisplayName("BUILD group has construction commands and no automation")
    void buildGroup() {
        List<String> snippets = snippets(GrammarReference.Group.BUILD);

        assertTrue(anyContains(snippets, "new st;"));
        assertTrue(anyContains(snippets, "write 5;"));
        assertTrue(anyContains(snippets, "semaphore 1 set closed;"));
        assertFalse(anyContains(snippets, "create itinerary"));
    }

    @Test
    @DisplayName("PROGRAM group has the automation DSL and no settings")
    void programGroup() {
        List<String> snippets = snippets(GrammarReference.Group.PROGRAM);

        assertTrue(anyContains(snippets, "create itinerary"));
        assertTrue(anyContains(snippets, "sensor # on train enter"));
        assertFalse(anyContains(snippets, "threshold.WATER"));
        assertFalse(anyContains(snippets, "new st;"));
    }

    @Test
    @DisplayName("CONFIG group only has key=value settings")
    void configGroup() {
        List<String> snippets = snippets(GrammarReference.Group.CONFIG);

        assertTrue(anyContains(snippets, "threshold.WATER=130"));
        assertTrue(anyContains(snippets, "startingBalance=0"));
        assertFalse(anyContains(snippets, "new st;"));
        assertFalse(anyContains(snippets, "create itinerary"));
    }

    @Test
    @DisplayName("the full tree unions every group")
    void unionGroup() {
        List<String> full = new ArrayList<>();
        collect(GrammarReference.getReferenceTree(), full);

        assertTrue(anyContains(full, "new st;"));
        assertTrue(anyContains(full, "create itinerary"));
        assertTrue(anyContains(full, "threshold.WATER=130"));
    }
}
