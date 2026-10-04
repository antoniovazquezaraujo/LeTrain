package letrain.visitor.gdx3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the locomotive headlight ground pools (#690, iteration 2): flat quads whose radial falloff
 * is computed <em>per fragment</em> from the local quad coordinates, with normal alpha blending.
 *
 * <p>
 * The first iteration used a radial-gradient texture on additive blending, and the owner still saw
 * rounded squares/diamonds plus a blown-out white area. Cause: a 128x128 texture without mipmaps
 * aliases badly when minified at grazing angles (the smooth gradient breaks into grid-like blobs),
 * and additive blending sums overlapping pools past white. Both disappear here: there is no texture
 * to minify (the falloff is analytic, so it is smooth at any distance and angle) and alpha blending
 * can never exceed the pool colour no matter how many pools overlap.
 *
 * <p>
 * The mesh is a unit quad in the XZ plane; {@link HeadlightGlows} provides the per-source pose and
 * opacity. Depth is tested but not written, so the pool lies on the ground, stays behind the
 * locomotives and never hides the passes drawn afterwards.
 */
public class HeadlightGlowRenderer implements Disposable {
    private static final Logger log = LoggerFactory.getLogger(HeadlightGlowRenderer.class);

    private static final String VERTEX_SHADER = "attribute vec3 a_position;\n" //
            + "uniform mat4 u_projView;\n" //
            + "uniform mat4 u_world;\n" //
            + "varying vec2 v_local;\n" //
            + "void main() {\n" //
            + "    v_local = a_position.xz;\n" //
            + "    gl_Position = u_projView * u_world * vec4(a_position, 1.0);\n" //
            + "}\n";

    private static final String FRAGMENT_SHADER = "#ifdef GL_ES\n" //
            + "precision mediump float;\n" //
            + "#endif\n" //
            + "varying vec2 v_local;\n" //
            + "uniform vec4 u_color;\n" //
            + "void main() {\n" //
            + "    vec2 p = v_local * 2.0;\n" //
            + "    float f = clamp(1.0 - length(p), 0.0, 1.0);\n" //
            + "    f = f * f;\n" //
            + "    gl_FragColor = vec4(u_color.rgb, u_color.a * f);\n" //
            + "}\n";

    private Mesh mesh;
    private ShaderProgram shader;
    private int uProjView;
    private int uWorld;
    private int uColor;
    private final Matrix4 world = new Matrix4();

    /** Creates the GL resources; must run on the GL thread (the presenter's {@code create()}). */
    public void init() {
        mesh = new Mesh(true, 4, 6, new VertexAttribute(Usage.Position, 3, "a_position"));
        // Unit quad in the XZ plane, counter-clockwise seen from above.
        mesh.setVertices(
                new float[] {-0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f});
        mesh.setIndices(new short[] {0, 2, 1, 0, 3, 2});

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            log.error("Headlight glow shader failed to compile: {}", shader.getLog());
            return;
        }
        uProjView = shader.getUniformLocation("u_projView");
        uWorld = shader.getUniformLocation("u_world");
        uColor = shader.getUniformLocation("u_color");
    }

    /**
     * Draws one pool per source (already the nearest ones). {@code color} is the headlight tint and
     * {@code opacity} its peak strength (0 = off, day).
     */
    public void render(Camera camera, List<Headlights.Source> sources, Color color, float opacity) {
        if (mesh == null || shader == null || !shader.isCompiled() || sources.isEmpty()
                || opacity <= 0f) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix(uProjView, camera.combined);
        shader.setUniformf(uColor, color.r, color.g, color.b, opacity);
        for (Headlights.Source source : sources) {
            world.setToTranslation(HeadlightGlows.centerX(source), HeadlightGlows.GROUND_Y,
                    HeadlightGlows.centerZ(source));
            world.rotate(0f, 1f, 0f, HeadlightGlows.yawDegrees(source));
            world.scale(HeadlightGlows.WIDTH, 1f, HeadlightGlows.LENGTH);
            shader.setUniformMatrix(uWorld, world);
            mesh.render(shader, GL20.GL_TRIANGLES);
        }

        // Restore the state the earlier lit pass left behind for the following passes.
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }
}
