package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D route preview: console navigation whitelist")
class RoutePreviewConsolePolicyTest {

    @Test
    @DisplayName("navigation and read-only commands are allowed")
    void allowsNavigationCommands() {
        assertTrue(RoutePreviewConsolePolicy.isAllowed("go 1,2"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("g 1,2"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("face n"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("mark here"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("m here"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("info"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("ls"));
    }

    @Test
    @DisplayName("case, whitespace and semicolons are normalised")
    void normalisesCaseWhitespaceAndSemicolons() {
        assertTrue(RoutePreviewConsolePolicy.isAllowed("  GO   1,2 ;  FaCe   s ; "));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("go 1,2; ls; info"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed(";; go 1,2 ;;"));
        assertTrue(RoutePreviewConsolePolicy.isAllowed(""));
        assertTrue(RoutePreviewConsolePolicy.isAllowed("   "));
        assertTrue(RoutePreviewConsolePolicy.isAllowed(null));
    }

    @Test
    @DisplayName("editing, world and lifecycle commands are blocked")
    void blocksEditingCommands() {
        assertFalse(RoutePreviewConsolePolicy.isAllowed("del 1,2"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("new"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("new game"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("clear"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("slide n"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("turtle"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("undo"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("redo"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("load save"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("quit"));
    }

    @Test
    @DisplayName("a single blocked statement blocks the whole semicolon-separated line")
    void blocksTheWholeLineIfAnyStatementIsBlocked() {
        assertFalse(RoutePreviewConsolePolicy.isAllowed("go 1,2; del 1,2"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("ls; slide n; info"));
        assertFalse(RoutePreviewConsolePolicy.isAllowed("DEL 1,2; go 1,2"));
    }
}
