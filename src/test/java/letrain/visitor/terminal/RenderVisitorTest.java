package letrain.visitor.terminal;

import static org.mockito.Mockito.*;

import com.googlecode.lanterna.TextColor;
import java.util.List;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.terminal.TerminalView;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderVisitorTest {

    @Test
    @DisplayName(
            "visitRailTrack should paint rail with locomotive color when segment is locked by a train")
    void visitRailTrack_shouldPaintWithLocomotiveColor_whenSegmentIsLocked() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        Train train = mock(Train.class);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        when(train.getDirectorLinker()).thenReturn(loco);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of(train));

        // Inject model into visitor
        visitor.visitModel(model);

        // Render the track
        visitor.visitRailTrack(track);

        // Verify that view.setFgColor was called with GREEN_BRIGHT
        verify(view, atLeastOnce()).setFgColor(TextColor.ANSI.GREEN_BRIGHT);
    }

    @Test
    @DisplayName("visitRailTrack should paint rail with standard color when segment is not locked")
    void visitRailTrack_shouldPaintWithStandardColor_whenSegmentIsNotLocked() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).setFgColor(TextColor.ANSI.BLACK_BRIGHT);
    }

    @Test
    @DisplayName("visitLocomotive should paint locomotive with its assigned color")
    void visitLocomotive_shouldPaintLocomotiveWithAssignedColor() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        Locomotive loco = new Locomotive(1, "A", "CYAN_BRIGHT");
        RailTrack track = new RailTrack();
        track.setPosition(new Point(10, 10));
        loco.setTrack(track);
        loco.setPosition(new Point(10, 10));

        visitor.visitModel(model);
        visitor.visitLocomotive(loco);

        verify(view, atLeastOnce()).setFgColor(TextColor.ANSI.CYAN_BRIGHT);
    }
}
