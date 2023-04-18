package letrain.map;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    SimpleRouter router;

    @BeforeEach
    void setUp() {
        router = new SimpleRouter();
    }

    @Test
    void testGetDir() {
        router.addRoute(Dir.N, Dir.S);
        router.addRoute(Dir.S, Dir.N);
        assertTrue(router.getDir(Dir.N) == Dir.S);
        assertTrue(router.getDir(Dir.S) == Dir.N);
    }

    @Test
    void testGetFirstOpenDir() {
        router.addRoute(Dir.N, Dir.S);
        Dir result = router.getFirstOpenDir();
        assertNotNull(result);
    }

    @Test
    void testGetNumOpenDirs() {
        router.addRoute(Dir.N, Dir.S);
        router.addRoute(Dir.E, Dir.NW);
        int result = router.getNumRoutes();
        assertEquals(4, result);

        router.addRoute(Dir.E, Dir.W);
        assertEquals(5, router.getNumRoutes());
    }

    @Test
    void testAddRoute() {
        router.addRoute(Dir.E, Dir.W);
        assertEquals(Dir.W, router.getDir(Dir.E));
        assertNull(router.getDir(Dir.N));
    }

    @Test
    void testRemoveRoute() {
        router.addRoute(Dir.E, Dir.W);
        router.removeRoute(Dir.E, Dir.W);
        assertNull(router.getDir(Dir.E));
    }

    @Test
    void testAddingNewRoutesThatMakesACross() {
        router.addRoute(Dir.NE, Dir.SW);
        router.addRoute(Dir.N, Dir.S);
        assertEquals(Dir.S, router.getDir(Dir.N));
        assertEquals(Dir.N, router.getDir(Dir.S));
        assertEquals(Dir.SW, router.getDir(Dir.NE));
        assertEquals(Dir.NE, router.getDir(Dir.SW));
        assertFalse(router.isStraight());
        assertFalse(router.isCurve());
        assertTrue(router.isCross());
    }

    @Test
    void testRemovingRoutesTransformACrossInAStraight() {
        router.addRoute(Dir.NE, Dir.SW);
        router.addRoute(Dir.N, Dir.S);
        assertTrue(router.isCross());
        router.removeRoute(Dir.N, Dir.S);
        assertFalse(router.isCross());
        assertTrue(router.isStraight());
    }

    @Test
    void testAddDirAndRouterDontContainsAnyOfBoth() {
        router.addRoute(Dir.N, Dir.S);
        assertTrue(router.isStraight());
        router.addRoute(Dir.E, Dir.SW);
        assertTrue(router.isCross());
    }

    @Test
    void testAddDirAndRouterContainsOneOfBoth() {
        router.addRoute(Dir.N, Dir.S);
        assertTrue(router.isStraight());
        router.addRoute(Dir.N, Dir.SW);
        assertFalse(router.isCross());
        assertEquals(3, router.getNumRoutes());
        router.removeRoute(Dir.N, Dir.SW);
        router.addRoute(Dir.SW, Dir.N);
        assertFalse(router.isCross());
    }

    @Test
    void testAddDirAndRouterContainsTheConterRoute() {
        router.addRoute(Dir.N, Dir.S);
        assertTrue(router.isStraight());
        router.addRoute(Dir.S, Dir.NW);
        assertFalse(router.isCross());
        assertEquals(3, router.getNumRoutes());
        router.addRoute(Dir.NW, Dir.S);
        assertFalse(router.isCross());
    }

    @Test
    void changingDirections() {
        SimpleRouter router = new SimpleRouter();

        // Test adding a new route
        router.addRoute(Dir.E, Dir.W);
        assertEquals(Dir.W, router.getDir(Dir.E));
        assertEquals(Dir.E, router.getDir(Dir.W));
        assertTrue(router.isStraight());

        // Test adding a new non-straight route
        router.addRoute(Dir.W, Dir.NE);
        assertEquals(Dir.W, router.getDir(Dir.E));
        assertEquals(Dir.NE, router.getDir(Dir.W));
        assertFalse(router.isStraight());
        assertFalse(router.isCurve());

        // // Test using alternative route
        // assertTrue(router.isUsingAlternativeRoute());
        // assertEquals(Dir.NE, router.getDir(Dir.W));

        // // Test setting normal route
        // router.setNormalRoute();
        // assertFalse(router.isUsingAlternativeRoute());
        // assertEquals(Dir.E, router.getDir(Dir.W));

        // // Test setting alternative route again
        // router.setAlternativeRoute();
        // assertTrue(router.isUsingAlternativeRoute());
        // assertEquals(Dir.NE, router.getDir(Dir.W));
    }
}