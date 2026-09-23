package letrain.visitor.terminal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.googlecode.lanterna.TextColor;
import java.util.List;
import letrain.map.Dir;
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
    @DisplayName("visitRailTrack should paint rail with locomotive color when segment is locked by a train")
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
        RenderVisitor visitor =
                new RenderVisitor(view, new TerminalPalette(TerminalPalette.Depth.TRUECOLOR));

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

        // day rail token of the light family
        verify(view, atLeastOnce()).setFgColor(new TextColor.RGB(50, 50, 55));
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

    @Test
    @DisplayName("visitRailTrack does not draw tunnel rail outside Rails mode")
    void visitRailTrack_shouldNotDrawTunnelRail_whenNotInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL);

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, never()).set(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("visitRailTrack draws tunnel rail in Rails mode")
    void visitRailTrack_shouldDrawTunnelRail_inRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.RAILS);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).set(eq(3), eq(3), anyString());
    }

    @Test
    @DisplayName("visitRailTrack keeps tunnel gates visible outside Rails mode")
    void visitRailTrack_shouldDrawTunnelGate_whenNotInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor = new RenderVisitor(view);

        Model model = mock(Model.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(3, 3));
        track.setVisualType(RailTrack.VisualType.TUNNEL_GATE);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).set(eq(3), eq(3), anyString());
    }

    @Test
    @DisplayName("the rail token follows the clock into the night palette")
    void visitRailTrack_shouldUseNightColor_whenClockIsNight() {
        TerminalView view = mock(TerminalView.class);
        RenderVisitor visitor =
                new RenderVisitor(view, new TerminalPalette(TerminalPalette.Depth.TRUECOLOR));

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        RailwayGraph graph = mock(RailwayGraph.class);
        BlockManager blockManager = mock(BlockManager.class);
        Segment segment = mock(Segment.class);

        RailTrack track = new RailTrack();
        track.setPosition(new Point(5, 5));

        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getRailwayGraph()).thenReturn(graph);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(graph.getSegment(track)).thenReturn(segment);
        when(blockManager.getOwners(segment)).thenReturn(List.of());

        visitor.visitModel(model);
        visitor.visitRailTrack(track);

        verify(view, atLeastOnce()).setFgColor(new TextColor.RGB(150, 150, 160));
    }

    @Test
    @DisplayName("the visitor resolves the banded palette, not the drifting clock ratio")
    void visitModel_shouldUseBandedPalette() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(0.30f);
        visitor.visitModel(model);

        // 0.30 belongs to the band the stepper picks
        float band = TerminalPalette.band(0.30f, -1f);
        TextColor expected =
                palette.colorOf(TerminalPalette.rgbFor(band).get(TerminalPalette.Token.RAIL));
        visitor.visitRailTrack(new RailTrack());
        verify(view, atLeastOnce()).setFgColor(expected);
    }

    @Test
    @DisplayName("at night a locomotive casts a headlight beam on the rail ahead")
    void visitModel_shouldCastHeadlightBeam_atNight() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("by day the headlight stays off")
    void visitModel_shouldNotCastHeadlightBeam_byDay() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(0f);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    /** Fondo esperado de una celda iluminada con factor {@code (dx, dy)} respecto a la loco. */
    private static TextColor litBoard(TerminalPalette palette, int dx, int dy) {
        float lit = Headlight.factor(dx, dy, Dir.E) * Headlight.MAX_LIGHT;
        int nightBoard = TerminalPalette.rgbFor(1f).get(TerminalPalette.Token.BOARD);
        int dayBoard = TerminalPalette.rgbFor(0f).get(TerminalPalette.Token.BOARD);
        return palette.colorOf(TerminalPalette.mix(nightBoard, dayBoard, lit));
    }

    @Test
    @DisplayName("a locomotive hidden inside a tunnel casts no light outside Rails mode")
    void visitModel_shouldHideHeadlight_whenLocomotiveIsInsideTunnel() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack tunnel = new RailTrack();
        tunnel.setPosition(new Point(5, 5));
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        loco.setTrack(tunnel);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("in Rails mode tunnels are visible, so the headlight is too")
    void visitModel_shouldCastHeadlight_insideTunnel_whileInRailsMode() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.RAILS);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack tunnel = new RailTrack();
        tunnel.setPosition(new Point(5, 5));
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        loco.setTrack(tunnel);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack ahead = new RailTrack();
        ahead.setPosition(new Point(6, 5));
        visitor.visitRailTrack(ahead);

        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 1, 0));
    }

    @Test
    @DisplayName("the beam stops at hidden tunnel cells instead of lighting through them")
    void visitModel_shouldNotLightHiddenTunnelCells() {
        TerminalView view = mock(TerminalView.class);
        TerminalPalette palette = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        RenderVisitor visitor = new RenderVisitor(view, palette);

        Model model = mock(Model.class);
        letrain.time.GameClock clock = mock(letrain.time.GameClock.class);
        when(model.getGameClock()).thenReturn(clock);
        when(clock.getDayNightRatio()).thenReturn(1f);
        when(model.getMode()).thenReturn(letrain.mvp.Model.GameMode.DRIVE);

        letrain.map.impl.RailMap railMap = mock(letrain.map.impl.RailMap.class);
        RailTrack tunnel = new RailTrack();
        tunnel.setVisualType(RailTrack.VisualType.TUNNEL);
        when(railMap.getTrackAt(6, 5)).thenReturn(tunnel);
        when(model.getRailMap()).thenReturn(railMap);

        Locomotive loco = new Locomotive(1, "A", "GREEN_BRIGHT");
        RailTrack locoTrack = new RailTrack();
        locoTrack.setPosition(new Point(5, 5));
        loco.setTrack(locoTrack);
        loco.setPosition(new Point(5, 5));
        loco.setDir(Dir.E);
        when(model.getLocomotives()).thenReturn(List.of(loco));

        visitor.visitModel(model);

        RailTrack throughTunnel = new RailTrack();
        throughTunnel.setPosition(new Point(6, 5));
        visitor.visitRailTrack(throughTunnel);
        RailTrack diagonal = new RailTrack();
        diagonal.setPosition(new Point(7, 6));
        visitor.visitRailTrack(diagonal);

        verify(view, never()).setBgColor(litBoard(palette, 1, 0));
        verify(view, atLeastOnce()).setBgColor(litBoard(palette, 2, 1));
    }
}
