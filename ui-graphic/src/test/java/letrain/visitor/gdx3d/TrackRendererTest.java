package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackRenderer blocked-track highlighting follows ui.highlightBlockedTracks")
class TrackRendererTest {

    @Test
    @DisplayName("highlight off: a locked segment returns no livery colour")
    void getTrackBlockedColor_shouldReturnNull_whenHighlightingIsOff() {
        RailTrack track = lockedTrack();
        TrackRenderer renderer = newRenderer();
        renderer.updateState(modelWithLockedSegment(track, false), null, 1f, false);

        assertNull(renderer.getTrackBlockedColor(track));
    }

    @Test
    @DisplayName("highlight on: a locked segment returns the locomotive colour")
    void getTrackBlockedColor_shouldReturnLocomotiveColor_whenHighlightingIsOn() {
        RailTrack track = lockedTrack();
        TrackRenderer renderer = newRenderer();
        renderer.updateState(modelWithLockedSegment(track, true), null, 1f, false);

        assertEquals(VehicleRenderer.getLibGdxColor("GREEN_BRIGHT"),
                renderer.getTrackBlockedColor(track));
    }

    /**
     * The resource context is mocked (no GL needed); the stubbed attenuation is a pass-through so
     * the test observes the raw livery colour the renderer would hand to the shader.
     */
    private static TrackRenderer newRenderer() {
        Gdx3DResourceContext context = mock(Gdx3DResourceContext.class);
        when(context.attenuatePlayerColor(any(Color.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new TrackRenderer(context, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static RailTrack lockedTrack() {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));
        return track;
    }

    private static Model modelWithLockedSegment(RailTrack track, boolean highlight) {
        Model model = mock(Model.class);
        EconomyManager economy = mock(EconomyManager.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        Train train = mock(Train.class);
        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");

        when(economy.isHighlightBlockedTracks()).thenReturn(highlight);
        when(model.getEconomyManager()).thenReturn(economy);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of(train));
        when(train.getDirectorLinker()).thenReturn(loco);
        return model;
    }
}
