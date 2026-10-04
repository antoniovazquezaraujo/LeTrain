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
 * Draws the locomotive headlight ground pools (#690): flat quads whose radial falloff is computed
 * <em>per fragment</em> from the local quad coordinates, with normal alpha blending.
 *
 * <p>
 * Two issues were fixed here over the iterations: a radial-gradient texture aliased into
 * grid-shaped diamonds when minified at grazing angles (now the falloff is analytic, so there is
 * nothing to minify), and the pool plane sat below the per-cell ballast boxes, whose depth punched
 * grid-shaped holes in the glow (now {@link HeadlightGlows#GROUND_Y} sits above the ballast, below
 * the rails and the locomotive).
 *
 * <p>
 * The falloff is the simple quadratic {@code clamp(1 - r)^2}, which keeps the pool compact and
 * bright in the centre while still fading to zero exactly at the quad edge; depth is tested but not
 * written, so the pool lies on the ground and stays behind the locomotives.
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
     * Draws one pool per source (already the nearest ones). {@code color} is the headlight tint;
     * the day/night gate lives in {@link HeadlightGlows#opacity}.
     */
    public void render(Camera camera, List<Headlights.Source> sources, Color color,
            double dayNightRatio) {
        float alpha = HeadlightGlows.opacity(dayNightRatio);
        if (mesh == null || shader == null || !shader.isCompiled() || sources.isEmpty()
                || alpha <= 0f) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix(uProjView, camera.combined);
        shader.setUniformf(uColor, color.r, color.g, color.b, alpha);
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
