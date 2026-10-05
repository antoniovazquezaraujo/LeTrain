package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.vehicle.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("3D cursor locate ping (#696)")
class InfrastructureRendererCursorPingTest {

    /**
     * GL-free stand-in for the real models: an empty model plus one material gives the instance a
     * real transform and a material the renderer can mutate.
     */
    private static ModelInstance whiteInstance() {
        ModelInstance instance = new ModelInstance(new Model());
        instance.materials.add(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        return instance;
    }

    @Test
    @DisplayName("an idle cursor adds no ping disc")
    void should_NotAddPingDisc_WhenIdle() {
        List<ModelInstance> instances = new ArrayList<>();
        InfrastructureRenderer renderer = newRenderer(instances, 5_000L);

        renderer.visitCursor(cursor());

        assertEquals(2, instances.size()); // cursor + ghost
    }

    @Test
    @DisplayName("an active ping adds one blended disc at its sweep scale")
    void should_AddPingDisc_WhenPinging() {
        List<ModelInstance> instances = new ArrayList<>();
        InfrastructureRenderer renderer = newRenderer(instances, 2_450L);
        Cursor cursor = cursor();
        cursor.ping(2_000L);

        renderer.visitCursor(cursor);

        assertEquals(3, instances.size()); // cursor + ghost + ping
        ModelInstance ping = instances.get(2);
        assertNotNull(ping.materials.get(0).get(BlendingAttribute.Type));
        // Halfway through the sweep: 1 + 2.5 * 0.5 of scale and 0.9 * 0.5 of opacity.
        assertEquals(2.25f, ping.transform.getScaleX(), 1e-4f);
        assertEquals(0.45f,
                ((BlendingAttribute) ping.materials.get(0).get(BlendingAttribute.Type)).opacity,
                1e-4f);
    }

    @Test
    @DisplayName("an expired ping adds no disc")
    void should_NotAddPingDisc_WhenExpired() {
        List<ModelInstance> instances = new ArrayList<>();
        InfrastructureRenderer renderer = newRenderer(instances, 2_000L);
        Cursor cursor = cursor();
        cursor.ping(1_000L);

        renderer.visitCursor(cursor);

        assertEquals(2, instances.size());
    }

    private static Cursor cursor() {
        Cursor cursor = new Cursor();
        cursor.setPosition(new Point(3, 4));
        cursor.setDir(Dir.E);
        return cursor;
    }

    /** Resource context is mocked: models carry no GL state, only transforms and materials. */
    private static InfrastructureRenderer newRenderer(List<ModelInstance> instances, long now) {
        Gdx3DResourceContext context = mock(Gdx3DResourceContext.class);
        when(context.getModelInstance(any())).thenAnswer(invocation -> whiteInstance());
        InfrastructureRenderer renderer = new InfrastructureRenderer(context, instances,
                new ArrayList<>(), new ArrayList<>(), mock(TrackRenderer.class));
        renderer.setClock(() -> now);
        return renderer;
    }
}
