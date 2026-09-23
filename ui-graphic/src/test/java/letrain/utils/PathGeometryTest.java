package letrain.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import letrain.map.Dir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Path geometry: right-hand side of a direction")
class PathGeometryTest {

    @Test
    @DisplayName("facing east the right is south, like the 2D terminal's getRightSide")
    void should_ReturnRightSide() {
        assertEquals(0f, PathGeometry.getRightX(Dir.E), 1e-6);
        assertEquals(0.5f, PathGeometry.getRightZ(Dir.E), 1e-6, "facing E, right is S (+Z)");

        assertEquals(0.5f, PathGeometry.getRightX(Dir.N), 1e-6, "facing N, right is E (+X)");
        assertEquals(0f, PathGeometry.getRightZ(Dir.N), 1e-6);

        assertEquals(0f, PathGeometry.getRightX(Dir.W), 1e-6);
        assertEquals(-0.5f, PathGeometry.getRightZ(Dir.W), 1e-6, "facing W, right is N (-Z)");

        assertEquals(-0.5f, PathGeometry.getRightX(Dir.S), 1e-6, "facing S, right is W (-X)");
        assertEquals(0f, PathGeometry.getRightZ(Dir.S), 1e-6);
    }

    @Test
    @DisplayName("diagonals follow the same rotation and null is neutral")
    void should_CoverDiagonalsAndNull() {
        // Right of NE (1) is SE (7): (+X, +Z)
        assertEquals(0.5f, PathGeometry.getRightX(Dir.NE), 1e-6);
        assertEquals(0.5f, PathGeometry.getRightZ(Dir.NE), 1e-6);
        // Right of SE (7) is SW (5): (-X, +Z)
        assertEquals(-0.5f, PathGeometry.getRightX(Dir.SE), 1e-6);
        assertEquals(0.5f, PathGeometry.getRightZ(Dir.SE), 1e-6);

        assertEquals(0f, PathGeometry.getRightX(null), 1e-6);
        assertEquals(0f, PathGeometry.getRightZ(null), 1e-6);
    }
}
